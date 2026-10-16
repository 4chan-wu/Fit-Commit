package com.fitcommit.app.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.concurrent.atomic.AtomicLong;

public final class PoseLandmarkerService implements ImageAnalysis.Analyzer, AutoCloseable {
    public interface Listener {
        void onPoseResult(PoseLandmarkerResult result, int imageWidth, int imageHeight);

        void onPoseError(String message);
    }

    private static final String MODEL_NAME = "pose_landmarker_full.task";
    private final Listener listener;
    private final AtomicLong lastTimestamp = new AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicBoolean processing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private PoseLandmarker landmarker;
    private Bitmap inFlightBitmap;

    public PoseLandmarkerService(Context context, Listener listener) {
        this.listener = listener;
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_NAME)
                    .build();
            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setNumPoses(1)
                            .setMinPoseDetectionConfidence(0.65f)
                            .setMinPosePresenceConfidence(0.65f)
                            .setMinTrackingConfidence(0.65f)
                            .setResultListener(this::onResult)
                            .setErrorListener(error -> {
                                releaseInFlightBitmap();
                                listener.onPoseError(safeMessage(error));
                            })
                            .build();
            landmarker = PoseLandmarker.createFromOptions(context.getApplicationContext(), options);
        } catch (RuntimeException error) {
            listener.onPoseError("신체 분석 모델을 준비하지 못했습니다. " + safeMessage(error));
        }
    }

    public boolean isReady() {
        return landmarker != null;
    }

    @Override
    public void analyze(ImageProxy imageProxy) {
        if (landmarker == null) {
            imageProxy.close();
            return;
        }

        if (processing.get()) {
            imageProxy.close();
            return;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        try {
            Bitmap source = Bitmap.createBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            imageProxy.getPlanes()[0].getBuffer().rewind();
            source.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            if (!analyzeBitmap(source, rotation)) source.recycle();
        } catch (RuntimeException error) {
            listener.onPoseError(safeMessage(error));
        } finally {
            imageProxy.close();
        }
    }

    public boolean analyzeBitmap(Bitmap source, int rotationDegrees) {
        if (landmarker == null || source == null || !processing.compareAndSet(false, true)) return false;
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.getWidth(),
                    source.getHeight(),
                    matrix,
                    true
            );
            if (rotated != source) source.recycle();
            synchronized (this) {
                inFlightBitmap = rotated;
            }
            MPImage image = new BitmapImageBuilder(rotated).build();
            landmarker.detectAsync(image, nextTimestamp());
            return true;
        } catch (RuntimeException error) {
            releaseInFlightBitmap();
            listener.onPoseError(safeMessage(error));
            return true;
        }
    }

    private void onResult(PoseLandmarkerResult result, MPImage image) {
        try {
            listener.onPoseResult(result, image.getWidth(), image.getHeight());
        } finally {
            image.close();
            releaseInFlightBitmap();
        }
    }

    private void releaseInFlightBitmap() {
        synchronized (this) {
            if (inFlightBitmap != null && !inFlightBitmap.isRecycled()) inFlightBitmap.recycle();
            inFlightBitmap = null;
        }
        processing.set(false);
    }

    private long nextTimestamp() {
        long now = SystemClock.uptimeMillis();
        while (true) {
            long previous = lastTimestamp.get();
            long next = Math.max(now, previous + 1L);
            if (lastTimestamp.compareAndSet(previous, next)) return next;
        }
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
    }

    @Override
    public void close() {
        if (landmarker != null) {
            landmarker.close();
            landmarker = null;
        }
        releaseInFlightBitmap();
    }
}
