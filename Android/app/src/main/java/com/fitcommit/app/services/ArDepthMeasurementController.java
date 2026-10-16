package com.fitcommit.app.services;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import com.fitcommit.app.views.ArCameraBackgroundRenderer;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class ArDepthMeasurementController implements GLSurfaceView.Renderer, AutoCloseable {
    public enum StartResult { ACTIVE, INSTALL_PENDING, UNAVAILABLE }

    public interface Listener {
        void onDepthHeight(float heightMeters);
        void onDepthStatus(String message);
    }

    private static final long POSE_INTERVAL_NS = 180_000_000L;
    private final Activity activity;
    private final GLSurfaceView surfaceView;
    private final PoseLandmarkerService poseService;
    private final Listener listener;
    private final ArCameraBackgroundRenderer backgroundRenderer = new ArCameraBackgroundRenderer();
    private final Object poseLock = new Object();
    private final List<Float> recentHeights = new ArrayList<>();
    private Session session;
    private PoseSample poseSample;
    private boolean installRequested;
    private boolean resumed;
    private boolean geometryDirty = true;
    private int viewportWidth;
    private int viewportHeight;
    private int cameraRotationDegrees = 90;
    private int sourceWidth;
    private int sourceHeight;
    private long lastPoseFrameTimestamp;
    private long processedPoseVersion;
    private long poseVersion;

    public ArDepthMeasurementController(
            Activity activity,
            GLSurfaceView surfaceView,
            PoseLandmarkerService poseService,
            Listener listener
    ) {
        this.activity = activity;
        this.surfaceView = surfaceView;
        this.poseService = poseService;
        this.listener = listener;
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public StartResult start() {
        try {
            if (session == null) {
                ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(activity);
                if (availability.isUnsupported()) return StartResult.UNAVAILABLE;
                ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance()
                        .requestInstall(activity, !installRequested);
                if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true;
                    return StartResult.INSTALL_PENDING;
                }
                session = new Session(activity);
                if (!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    close();
                    return StartResult.UNAVAILABLE;
                }
                Config config = session.getConfig();
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
                config.setFocusMode(Config.FocusMode.AUTO);
                session.configure(config);
                cameraRotationDegrees = resolveCameraRotationDegrees();
            }
            session.resume();
            resumed = true;
            surfaceView.onResume();
            listener.onDepthStatus("휴대폰을 천천히 움직여 바닥과 사람을 인식해주세요.");
            return StartResult.ACTIVE;
        } catch (Exception error) {
            listener.onDepthStatus("AR 깊이 측정을 시작할 수 없어 키 입력 모드로 전환합니다.");
            close();
            return StartResult.UNAVAILABLE;
        }
    }

    public void pause() {
        if (!resumed) return;
        surfaceView.onPause();
        if (session != null) session.pause();
        resumed = false;
    }

    public void updatePose(PoseLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) return;
        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
        if (landmarks.size() < 33) return;
        NormalizedLandmark nose = landmarks.get(0);
        NormalizedLandmark leftShoulder = landmarks.get(11);
        NormalizedLandmark rightShoulder = landmarks.get(12);
        NormalizedLandmark leftHeel = landmarks.get(29);
        NormalizedLandmark rightHeel = landmarks.get(30);
        NormalizedLandmark leftFoot = landmarks.get(31);
        NormalizedLandmark rightFoot = landmarks.get(32);
        if (!visible(nose) || !visible(leftShoulder) || !visible(rightShoulder)
                || !visible(leftHeel) || !visible(rightHeel)) return;

        float shoulderY = (leftShoulder.y() + rightShoulder.y()) * 0.5f;
        float headTopY = clamp(nose.y() - Math.abs(shoulderY - nose.y()) * 0.45f, 0.01f, 0.99f);
        float footX = (leftHeel.x() + rightHeel.x() + leftFoot.x() + rightFoot.x()) * 0.25f;
        float footY = Math.max(Math.max(leftHeel.y(), rightHeel.y()), Math.max(leftFoot.y(), rightFoot.y()));
        synchronized (poseLock) {
            poseSample = new PoseSample(nose.x(), headTopY, footX, clamp(footY, 0.01f, 0.99f));
            poseVersion++;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.06f, 0.07f, 0.09f, 1f);
        backgroundRenderer.create();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        geometryDirty = true;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session currentSession = session;
        if (!resumed || currentSession == null || backgroundRenderer.getTextureId() < 0) return;
        try {
            if (geometryDirty && viewportWidth > 0 && viewportHeight > 0) {
                currentSession.setDisplayGeometry(activity.getWindowManager().getDefaultDisplay().getRotation(), viewportWidth, viewportHeight);
                geometryDirty = false;
            }
            currentSession.setCameraTextureName(backgroundRenderer.getTextureId());
            Frame frame = currentSession.update();
            backgroundRenderer.draw(frame);
            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) return;
            analyzeCameraFrame(frame);
            calculateDepthHeight(frame);
        } catch (CameraNotAvailableException error) {
            listener.onDepthStatus("카메라를 사용할 수 없습니다. 앱을 다시 실행해주세요.");
        } catch (RuntimeException ignored) {
            // A transient depth/camera frame failure is expected while ARCore initializes.
        }
    }

    private void analyzeCameraFrame(Frame frame) {
        if (frame.getTimestamp() - lastPoseFrameTimestamp < POSE_INTERVAL_NS) return;
        try (Image image = frame.acquireCameraImage()) {
            sourceWidth = image.getCropRect().width();
            sourceHeight = image.getCropRect().height();
            Bitmap bitmap = yuvToBitmap(image);
            if (poseService.analyzeBitmap(bitmap, cameraRotationDegrees)) {
                lastPoseFrameTimestamp = frame.getTimestamp();
            } else {
                bitmap.recycle();
            }
        } catch (NotYetAvailableException ignored) {
        }
    }

    private void calculateDepthHeight(Frame frame) {
        PoseSample sample;
        long version;
        synchronized (poseLock) {
            sample = poseSample;
            version = poseVersion;
        }
        if (sample == null || version == processedPoseVersion || sourceWidth <= 0 || sourceHeight <= 0) return;
        processedPoseVersion = version;

        float[] imagePoints = new float[4];
        toSourcePixels(sample.headX, sample.headY, imagePoints, 0);
        toSourcePixels(sample.footX, sample.footY, imagePoints, 2);
        FloatBuffer input = FloatBuffer.wrap(imagePoints);
        FloatBuffer output = FloatBuffer.allocate(4);
        frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, input, Coordinates2d.VIEW, output);
        float[] viewPoints = output.array();
        Pose head = depthHit(frame, viewPoints[0], viewPoints[1]);
        Pose foot = depthHit(frame, viewPoints[2], viewPoints[3]);
        if (head == null || foot == null) return;

        float horizontalOffset = (float) Math.hypot(head.tx() - foot.tx(), head.tz() - foot.tz());
        float height = Math.abs(head.ty() - foot.ty());
        if (horizontalOffset > 0.40f || height < 1.0f || height > 2.3f) return;
        recentHeights.add(height);
        if (recentHeights.size() > 7) recentHeights.remove(0);
        if (recentHeights.size() < 5) return;
        List<Float> sorted = new ArrayList<>(recentHeights);
        Collections.sort(sorted);
        if (sorted.get(sorted.size() - 1) - sorted.get(0) > 0.06f) return;
        listener.onDepthHeight(sorted.get(sorted.size() / 2));
    }

    private Pose depthHit(Frame frame, float x, float y) {
        for (HitResult hit : frame.hitTest(x, y)) {
            if (hit.getTrackable() instanceof DepthPoint) return hit.getHitPose();
        }
        return null;
    }

    private void toSourcePixels(float rotatedX, float rotatedY, float[] output, int offset) {
        if (cameraRotationDegrees == 90) {
            output[offset] = rotatedY * sourceWidth;
            output[offset + 1] = (1f - rotatedX) * sourceHeight;
        } else if (cameraRotationDegrees == 270) {
            output[offset] = (1f - rotatedY) * sourceWidth;
            output[offset + 1] = rotatedX * sourceHeight;
        } else if (cameraRotationDegrees == 180) {
            output[offset] = (1f - rotatedX) * sourceWidth;
            output[offset + 1] = (1f - rotatedY) * sourceHeight;
        } else {
            output[offset] = rotatedX * sourceWidth;
            output[offset + 1] = rotatedY * sourceHeight;
        }
    }

    private int resolveCameraRotationDegrees() {
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(session.getCameraConfig().getCameraId());
            Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int displayDegrees = rotationDegrees(activity.getWindowManager().getDefaultDisplay().getRotation());
            return ((sensorOrientation == null ? 90 : sensorOrientation) - displayDegrees + 360) % 360;
        } catch (Exception ignored) {
            return 90;
        }
    }

    private static int rotationDegrees(int rotation) {
        if (rotation == Surface.ROTATION_90) return 90;
        if (rotation == Surface.ROTATION_180) return 180;
        if (rotation == Surface.ROTATION_270) return 270;
        return 0;
    }

    private static Bitmap yuvToBitmap(Image image) {
        Rect crop = image.getCropRect();
        int width = crop.width();
        int height = crop.height();
        int[] colors = new int[width * height];
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int uRowStride = planes[1].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vRowStride = planes[2].getRowStride();
        int vPixelStride = planes[2].getPixelStride();
        for (int y = 0; y < height; y++) {
            int sourceY = y + crop.top;
            for (int x = 0; x < width; x++) {
                int sourceX = x + crop.left;
                int yValue = yBuffer.get(sourceY * yRowStride + sourceX * yPixelStride) & 0xff;
                int uValue = uBuffer.get((sourceY / 2) * uRowStride + (sourceX / 2) * uPixelStride) & 0xff;
                int vValue = vBuffer.get((sourceY / 2) * vRowStride + (sourceX / 2) * vPixelStride) & 0xff;
                int c = Math.max(0, yValue - 16);
                int d = uValue - 128;
                int e = vValue - 128;
                int red = clampColor((298 * c + 409 * e + 128) >> 8);
                int green = clampColor((298 * c - 100 * d - 208 * e + 128) >> 8);
                int blue = clampColor((298 * c + 516 * d + 128) >> 8);
                colors[y * width + x] = Color.rgb(red, green, blue);
            }
        }
        return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean visible(NormalizedLandmark landmark) {
        return landmark.visibility().orElse(0f) >= 0.65f;
    }

    @Override
    public void close() {
        resumed = false;
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private static final class PoseSample {
        final float headX;
        final float headY;
        final float footX;
        final float footY;

        PoseSample(float headX, float headY, float footX, float footY) {
            this.headX = headX;
            this.headY = headY;
            this.footX = footX;
            this.footY = footY;
        }
    }
}
