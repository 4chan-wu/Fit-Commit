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
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class ArDepthMeasurementController implements GLSurfaceView.Renderer, AutoCloseable {
    public enum StartResult { ACTIVE, INSTALL_PENDING, UNAVAILABLE }

    public interface Listener {
        void onArFrameReady();
        void onArFailure(String message);
        void onDepthHeight(float heightMeters);
        void onDepthStatus(String message);
    }

    private static final long POSE_INTERVAL_NS = 180_000_000L;
    private static final int MIN_DEPTH_CONFIDENCE = 128;
    private static final int MIN_DEPTH_MILLIMETERS = 500;
    private static final int MAX_DEPTH_MILLIMETERS = 5_000;
    private static final float BODY_DEPTH_TOLERANCE_METERS = 0.55f;
    private static final int STABLE_SAMPLE_COUNT = 7;
    private static final int MAX_STABLE_SAMPLES = 11;
    private static final long STABLE_SAMPLE_RESET_NS = 1_500_000_000L;
    private final Activity activity;
    private final GLSurfaceView surfaceView;
    private final PoseLandmarkerService poseService;
    private final Listener listener;
    private final ArCameraBackgroundRenderer backgroundRenderer = new ArCameraBackgroundRenderer();
    private final Object poseLock = new Object();
    private final List<Float> recentHeights = new ArrayList<>();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean imageConversionRunning = new AtomicBoolean(false);
    private Session session;
    private PoseSample poseSample;
    private boolean installRequested;
    private boolean resumed;
    private boolean geometryDirty = true;
    private int viewportWidth;
    private int viewportHeight;
    private int cameraRotationDegrees = 90;
    private volatile int sourceWidth;
    private volatile int sourceHeight;
    private long lastPoseFrameTimestamp;
    private long processedPoseVersion;
    private long poseVersion;
    private long lastRawDepthTimestamp;
    private long lastHeightCandidateAt;
    private boolean frameReady;
    private boolean fatalErrorReported;
    private volatile boolean closed;
    private int consecutiveRenderFailures;

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
        if (closed) return StartResult.UNAVAILABLE;
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
            frameReady = false;
            fatalErrorReported = false;
            consecutiveRenderFailures = 0;
            surfaceView.onResume();
            listener.onDepthStatus(activity.getString(com.fitcommit.app.R.string.body_ar_tracking_guide));
            return StartResult.ACTIVE;
        } catch (Exception error) {
            listener.onDepthStatus(activity.getString(com.fitcommit.app.R.string.body_ar_start_failed));
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
        NormalizedLandmark leftHip = landmarks.get(23);
        NormalizedLandmark rightHip = landmarks.get(24);
        NormalizedLandmark leftHeel = landmarks.get(29);
        NormalizedLandmark rightHeel = landmarks.get(30);
        NormalizedLandmark leftFoot = landmarks.get(31);
        NormalizedLandmark rightFoot = landmarks.get(32);
        if (!visible(nose) || !visible(leftShoulder) || !visible(rightShoulder)
                || !visible(leftHeel) || !visible(rightHeel)
                || !visible(leftFoot) || !visible(rightFoot)) return;

        float shoulderY = (leftShoulder.y() + rightShoulder.y()) * 0.5f;
        float headTopY = clamp(nose.y() - Math.abs(shoulderY - nose.y()) * 0.55f, 0.01f, 0.99f);
        float shoulderX = (leftShoulder.x() + rightShoulder.x()) * 0.5f;
        float torsoX = shoulderX;
        float torsoY = shoulderY;
        if (visible(leftHip) && visible(rightHip)) {
            float hipX = (leftHip.x() + rightHip.x()) * 0.5f;
            float hipY = (leftHip.y() + rightHip.y()) * 0.5f;
            torsoX = (shoulderX + hipX) * 0.5f;
            torsoY = (shoulderY + hipY) * 0.5f;
        }
        float footX = (leftHeel.x() + rightHeel.x() + leftFoot.x() + rightFoot.x()) * 0.25f;
        float footY = Math.max(Math.max(leftHeel.y(), rightHeel.y()), Math.max(leftFoot.y(), rightFoot.y()));
        synchronized (poseLock) {
            poseSample = new PoseSample(
                    nose.x(), headTopY, torsoX, torsoY,
                    footX, clamp(footY, 0.01f, 0.99f)
            );
            poseVersion++;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.06f, 0.07f, 0.09f, 1f);
        try {
            backgroundRenderer.create();
        } catch (RuntimeException error) {
            reportFatalError(activity.getString(com.fitcommit.app.R.string.body_ar_render_failed));
        }
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
            boolean backgroundDrawn = backgroundRenderer.draw(frame);
            consecutiveRenderFailures = 0;
            if (!frameReady && backgroundDrawn) {
                frameReady = true;
                activity.runOnUiThread(listener::onArFrameReady);
            }
            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) return;
            analyzeCameraFrame(frame);
            calculateDepthHeight(frame);
        } catch (CameraNotAvailableException error) {
            reportFatalError(activity.getString(com.fitcommit.app.R.string.body_ar_camera_unavailable));
        } catch (RuntimeException error) {
            // ARCore can briefly fail while depth data is warming up. Fall back only
            // after repeated failures so the user is never left on a dark preview.
            consecutiveRenderFailures++;
            if (consecutiveRenderFailures >= 12) {
                reportFatalError(activity.getString(com.fitcommit.app.R.string.body_ar_render_failed));
            }
        }
    }

    private void analyzeCameraFrame(Frame frame) {
        long timestamp = frame.getTimestamp();
        if (timestamp - lastPoseFrameTimestamp < POSE_INTERVAL_NS
                || !imageConversionRunning.compareAndSet(false, true)) return;
        final Image image;
        try {
            image = frame.acquireCameraImage();
        } catch (NotYetAvailableException ignored) {
            imageConversionRunning.set(false);
            return;
        }
        lastPoseFrameTimestamp = timestamp;
        imageExecutor.execute(() -> {
            try (Image cameraImage = image) {
                sourceWidth = cameraImage.getCropRect().width();
                sourceHeight = cameraImage.getCropRect().height();
                Bitmap bitmap = yuvToBitmap(cameraImage);
                if (closed || !poseService.analyzeBitmap(bitmap, cameraRotationDegrees)) bitmap.recycle();
            } catch (RuntimeException error) {
                listener.onDepthStatus(activity.getString(com.fitcommit.app.R.string.body_pose_frame_skipped));
            } finally {
                imageConversionRunning.set(false);
            }
        });
    }

    private void reportFatalError(String message) {
        if (closed || fatalErrorReported) return;
        fatalErrorReported = true;
        activity.runOnUiThread(() -> listener.onArFailure(message));
    }

    private void calculateDepthHeight(Frame frame) {
        PoseSample sample;
        long version;
        synchronized (poseLock) {
            sample = poseSample;
            version = poseVersion;
        }
        if (sample == null || version == processedPoseVersion
                || sourceWidth <= 0 || sourceHeight <= 0) return;

        try (Image depthImage = frame.acquireRawDepthImage16Bits();
             Image confidenceImage = frame.acquireRawDepthConfidenceImage()) {
            if (depthImage.getTimestamp() == lastRawDepthTimestamp) return;
            lastRawDepthTimestamp = depthImage.getTimestamp();
            processedPoseVersion = version;

            float[] imagePoints = new float[6];
            toSourcePixels(sample.headX, sample.headY, imagePoints, 0);
            toSourcePixels(sample.torsoX, sample.torsoY, imagePoints, 2);
            toSourcePixels(sample.footX, sample.footY, imagePoints, 4);
            float[] texturePoints = new float[6];
            frame.transformCoordinates2d(
                    Coordinates2d.IMAGE_PIXELS,
                    imagePoints,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    texturePoints
            );

            RawDepthReader reader = new RawDepthReader(
                    depthImage,
                    confidenceImage,
                    frame.getCamera().getTextureIntrinsics(),
                    frame.getCamera().getPose()
            );
            DepthRegion torso = reader.sample(texturePoints[2], texturePoints[3], Float.NaN);
            if (torso == null) return;
            DepthRegion head = reader.sample(texturePoints[0], texturePoints[1], torso.depthMeters);
            DepthRegion foot = reader.sample(texturePoints[4], texturePoints[5], torso.depthMeters);
            if (head == null || foot == null) return;

            float horizontalOffset = (float) Math.hypot(
                    head.worldX - foot.worldX,
                    head.worldZ - foot.worldZ
            );
            // ARCore's world Y axis follows gravity, so this remains vertical even when
            // the phone itself is tilted. Head/foot neighborhoods suppress single-pixel noise.
            float height = Math.abs(head.worldY - foot.worldY);
            if (horizontalOffset > 0.45f || height < 1.0f || height > 2.3f) return;
            addStableHeight(height);
        } catch (NotYetAvailableException ignored) {
            // Raw depth commonly needs several frames and some device movement to warm up.
        }
    }

    private void addStableHeight(float height) {
        long now = System.nanoTime();
        if (lastHeightCandidateAt > 0L && now - lastHeightCandidateAt > STABLE_SAMPLE_RESET_NS) {
            recentHeights.clear();
        }
        lastHeightCandidateAt = now;
        recentHeights.add(height);
        if (recentHeights.size() > MAX_STABLE_SAMPLES) recentHeights.remove(0);
        if (recentHeights.size() < STABLE_SAMPLE_COUNT) return;

        List<Float> sorted = new ArrayList<>(recentHeights);
        Collections.sort(sorted);
        float median = sorted.get(sorted.size() / 2);
        List<Float> deviations = new ArrayList<>(sorted.size());
        for (float value : sorted) deviations.add(Math.abs(value - median));
        Collections.sort(deviations);
        float medianAbsoluteDeviation = deviations.get(deviations.size() / 2);
        if (medianAbsoluteDeviation > 0.025f) return;

        float total = 0f;
        int accepted = 0;
        float allowedDeviation = Math.max(0.025f, medianAbsoluteDeviation * 3f);
        for (float value : sorted) {
            if (Math.abs(value - median) <= allowedDeviation) {
                total += value;
                accepted++;
            }
        }
        if (accepted >= STABLE_SAMPLE_COUNT - 1) listener.onDepthHeight(total / accepted);
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
        closed = true;
        resumed = false;
        if (session != null) {
            session.close();
            session = null;
        }
        imageExecutor.shutdownNow();
    }

    private static final class PoseSample {
        final float headX;
        final float headY;
        final float torsoX;
        final float torsoY;
        final float footX;
        final float footY;

        PoseSample(
                float headX,
                float headY,
                float torsoX,
                float torsoY,
                float footX,
                float footY
        ) {
            this.headX = headX;
            this.headY = headY;
            this.torsoX = torsoX;
            this.torsoY = torsoY;
            this.footX = footX;
            this.footY = footY;
        }
    }

    private static final class DepthRegion {
        final float depthMeters;
        final float worldX;
        final float worldY;
        final float worldZ;

        DepthRegion(float depthMeters, float worldX, float worldY, float worldZ) {
            this.depthMeters = depthMeters;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
        }
    }

    /** Reads confidence-filtered raw depth pixels and converts them to ARCore world coordinates. */
    private static final class RawDepthReader {
        private final int width;
        private final int height;
        private final Image.Plane depthPlane;
        private final Image.Plane confidencePlane;
        private final ByteBuffer depthBuffer;
        private final ByteBuffer confidenceBuffer;
        private final float fx;
        private final float fy;
        private final float cx;
        private final float cy;
        private final Pose cameraPose;

        RawDepthReader(
                Image depthImage,
                Image confidenceImage,
                CameraIntrinsics intrinsics,
                Pose cameraPose
        ) {
            width = depthImage.getWidth();
            height = depthImage.getHeight();
            depthPlane = depthImage.getPlanes()[0];
            confidencePlane = confidenceImage.getPlanes()[0];
            depthBuffer = depthPlane.getBuffer().order(ByteOrder.LITTLE_ENDIAN);
            confidenceBuffer = confidencePlane.getBuffer();
            int[] intrinsicSize = intrinsics.getImageDimensions();
            float[] focalLength = intrinsics.getFocalLength();
            float[] principalPoint = intrinsics.getPrincipalPoint();
            fx = focalLength[0] * width / intrinsicSize[0];
            fy = focalLength[1] * height / intrinsicSize[1];
            cx = principalPoint[0] * width / intrinsicSize[0];
            cy = principalPoint[1] * height / intrinsicSize[1];
            this.cameraPose = cameraPose;
        }

        DepthRegion sample(float normalizedX, float normalizedY, float expectedDepthMeters) {
            if (!Float.isFinite(normalizedX) || !Float.isFinite(normalizedY)) return null;
            int centerX = Math.round(clamp(normalizedX, 0f, 1f) * (width - 1));
            int centerY = Math.round(clamp(normalizedY, 0f, 1f) * (height - 1));
            int radius = Math.max(3, Math.min(width, height) / 32);
            List<Float> depths = new ArrayList<>();
            List<Float> worldXs = new ArrayList<>();
            List<Float> worldYs = new ArrayList<>();
            List<Float> worldZs = new ArrayList<>();

            for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
                for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                    if ((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)
                            > radius * radius) continue;
                    int confidence = unsignedByte(confidenceBuffer, confidencePlane, x, y);
                    if (confidence < MIN_DEPTH_CONFIDENCE) continue;
                    int millimeters = unsignedShort(depthBuffer, depthPlane, x, y);
                    if (millimeters < MIN_DEPTH_MILLIMETERS || millimeters > MAX_DEPTH_MILLIMETERS) continue;
                    float meters = millimeters / 1000f;
                    if (Float.isFinite(expectedDepthMeters)
                            && Math.abs(meters - expectedDepthMeters) > BODY_DEPTH_TOLERANCE_METERS) continue;

                    float[] world = cameraPose.transformPoint(new float[] {
                            meters * (x - cx) / fx,
                            meters * (cy - y) / fy,
                            -meters
                    });
                    depths.add(meters);
                    worldXs.add(world[0]);
                    worldYs.add(world[1]);
                    worldZs.add(world[2]);
                }
            }
            if (depths.size() < 3) {
                if (!Float.isFinite(expectedDepthMeters)) return null;
                float[] world = cameraPose.transformPoint(new float[] {
                        expectedDepthMeters * (centerX - cx) / fx,
                        expectedDepthMeters * (cy - centerY) / fy,
                        -expectedDepthMeters
                });
                return new DepthRegion(expectedDepthMeters, world[0], world[1], world[2]);
            }
            return new DepthRegion(
                    median(depths), median(worldXs), median(worldYs), median(worldZs)
            );
        }

        private static int unsignedShort(ByteBuffer buffer, Image.Plane plane, int x, int y) {
            int index = y * plane.getRowStride() + x * plane.getPixelStride();
            return buffer.getShort(index) & 0xffff;
        }

        private static int unsignedByte(ByteBuffer buffer, Image.Plane plane, int x, int y) {
            int index = y * plane.getRowStride() + x * plane.getPixelStride();
            return buffer.get(index) & 0xff;
        }

        private static float median(List<Float> values) {
            Collections.sort(values);
            int middle = values.size() / 2;
            if ((values.size() & 1) == 1) return values.get(middle);
            return (values.get(middle - 1) + values.get(middle)) * 0.5f;
        }
    }
}
