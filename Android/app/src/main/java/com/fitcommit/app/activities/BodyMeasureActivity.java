package com.fitcommit.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.opengl.GLSurfaceView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fitcommit.app.R;
import com.fitcommit.app.models.BodyMeasurement;
import com.fitcommit.app.services.BodyMeasurementCalculator;
import com.fitcommit.app.services.BodyMeasurementStorage;
import com.fitcommit.app.services.BodyMeasurementService;
import com.fitcommit.app.services.ArDepthMeasurementController;
import com.fitcommit.app.services.PoseLandmarkerService;
import com.fitcommit.app.utils.UiUtils;
import com.fitcommit.app.views.PoseOverlayView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BodyMeasureActivity extends ComponentActivity implements
        PoseLandmarkerService.Listener,
        ArDepthMeasurementController.Listener {
    private static final int STABLE_FRAME_TARGET = 8;
    private static final long AR_PREVIEW_TIMEOUT_MS = 5_000L;

    private PreviewView previewView;
    private PoseOverlayView overlayView;
    private TextView statusText;
    private TextView liveHeightText;
    private TextView liveGuideText;
    private TextView measurementModeText;
    private EditText manualHeightInput;
    private Button measureButton;
    private View resultContainer;
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysis;
    private PoseLandmarkerService poseService;
    private ArDepthMeasurementController arDepthController;
    private BodyMeasurement smoothedMeasurement;
    private BodyMeasurement lastRawMeasurement;
    private BodyMeasurement latestPoseMeasurement;
    private int stableFrames;
    private boolean resultShown;
    private boolean arDepthMode;
    private boolean cameraXStarted;
    private float latestDepthHeightMeters;
    private long latestDepthHeightAt;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean arFrameReady;
    private final Runnable arPreviewTimeout = () -> {
        if (arDepthMode && !arFrameReady && !resultShown) {
            fallbackToManualCamera(getString(R.string.body_ar_preview_timeout));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startPreferredCamera();
                    } else {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText(R.string.body_permission_required);
                    }
                }
        );
        setContentView(R.layout.activity_body_measure);
        applyCameraInsets();

        previewView = findViewById(R.id.bodyCameraPreview);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        overlayView = findViewById(R.id.poseOverlay);
        statusText = findViewById(R.id.bodyCameraStatusText);
        liveHeightText = findViewById(R.id.liveHeightText);
        liveGuideText = findViewById(R.id.liveGuideText);
        measurementModeText = findViewById(R.id.measurementModeText);
        manualHeightInput = findViewById(R.id.manualHeightInput);
        measureButton = findViewById(R.id.measureBodyButton);
        resultContainer = findViewById(R.id.measurementResultContainer);
        cameraExecutor = Executors.newSingleThreadExecutor();

        findViewById(R.id.bodyCameraBackText).setOnClickListener(v -> finish());
        measureButton.setOnClickListener(v -> showMeasurementResult());
        findViewById(R.id.retakeMeasurementButton).setOnClickListener(v -> resetMeasurement());
        findViewById(R.id.saveMeasurementButton).setOnClickListener(v -> saveAndFinish());
        findViewById(R.id.useManualHeightButton).setOnClickListener(v -> switchToManualHeightMode());

        poseService = new PoseLandmarkerService(this, this);
        arDepthController = new ArDepthMeasurementController(
                this,
                (GLSurfaceView) findViewById(R.id.bodyArCameraPreview),
                poseService,
                this
        );
        manualHeightInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!arDepthMode && latestPoseMeasurement != null && !resultShown) {
                    updateStability(resolveMeasurement(latestPoseMeasurement));
                }
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        if (!poseService.isReady()) {
            statusText.setText(R.string.body_model_failed);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void applyCameraInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        );
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        View root = findViewById(R.id.bodyMeasureRoot);
        View topPanel = findViewById(R.id.bodyTopPanel);
        View bottomPanel = findViewById(R.id.bodyBottomPanel);
        int topPaddingStart = topPanel.getPaddingStart();
        int topPaddingTop = topPanel.getPaddingTop();
        int topPaddingEnd = topPanel.getPaddingEnd();
        int topPaddingBottom = topPanel.getPaddingBottom();
        ViewGroup.MarginLayoutParams initialBottomParams =
                (ViewGroup.MarginLayoutParams) bottomPanel.getLayoutParams();
        int baseBottomMargin = initialBottomParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            topPanel.setPaddingRelative(
                    topPaddingStart,
                    topPaddingTop + systemBars.top,
                    topPaddingEnd,
                    topPaddingBottom
            );
            ViewGroup.MarginLayoutParams bottomParams =
                    (ViewGroup.MarginLayoutParams) bottomPanel.getLayoutParams();
            bottomParams.bottomMargin = baseBottomMargin + systemBars.bottom;
            bottomPanel.setLayoutParams(bottomParams);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (poseService != null && poseService.isReady()
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startPreferredCamera();
        }
    }

    private void startPreferredCamera() {
        if (cameraXStarted) return;
        ArDepthMeasurementController.StartResult result = arDepthController.start();
        if (result == ArDepthMeasurementController.StartResult.INSTALL_PENDING) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(R.string.body_ar_preparing);
            return;
        }
        if (result == ArDepthMeasurementController.StartResult.ACTIVE) {
            arDepthMode = true;
            arFrameReady = false;
            findViewById(R.id.bodyCameraPreview).setVisibility(View.GONE);
            findViewById(R.id.bodyArCameraPreview).setVisibility(View.VISIBLE);
            manualHeightInput.setVisibility(View.GONE);
            findViewById(R.id.useManualHeightButton).setVisibility(View.VISIBLE);
            measurementModeText.setText(R.string.body_ar_mode);
            liveGuideText.setText(R.string.body_ar_guide);
            statusText.setText(R.string.body_ar_preparing);
            statusText.setVisibility(View.VISIBLE);
            mainHandler.removeCallbacks(arPreviewTimeout);
            mainHandler.postDelayed(arPreviewTimeout, AR_PREVIEW_TIMEOUT_MS);
            return;
        }
        enableManualHeightMode();
        startCamera();
    }

    private void enableManualHeightMode() {
        arDepthMode = false;
        findViewById(R.id.bodyArCameraPreview).setVisibility(View.GONE);
        findViewById(R.id.bodyCameraPreview).setVisibility(View.VISIBLE);
        manualHeightInput.setVisibility(View.VISIBLE);
        findViewById(R.id.useManualHeightButton).setVisibility(View.GONE);
        measurementModeText.setText(R.string.body_manual_mode);
        liveHeightText.setText(R.string.ui_032);
        liveGuideText.setText(R.string.body_manual_guide);
    }

    private void switchToManualHeightMode() {
        mainHandler.removeCallbacks(arPreviewTimeout);
        arDepthController.pause();
        arDepthController.close();
        latestDepthHeightMeters = 0f;
        latestDepthHeightAt = 0L;
        stableFrames = 0;
        smoothedMeasurement = null;
        lastRawMeasurement = null;
        enableManualHeightMode();
        startCamera();
    }

    private void fallbackToManualCamera(String reason) {
        if (!arDepthMode) return;
        mainHandler.removeCallbacks(arPreviewTimeout);
        arDepthController.pause();
        arDepthController.close();
        latestDepthHeightMeters = 0f;
        latestDepthHeightAt = 0L;
        arFrameReady = false;
        enableManualHeightMode();
        liveGuideText.setText(reason);
        startCamera();
    }

    private void startCamera() {
        if (cameraXStarted) return;
        cameraXStarted = true;
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(R.string.body_camera_preparing);
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, poseService);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
                statusText.setVisibility(View.GONE);
            } catch (Exception error) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(getString(R.string.body_camera_failed, safeMessage(error)));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onPoseResult(PoseLandmarkerResult result, int imageWidth, int imageHeight) {
        BodyMeasurement poseMeasurement = BodyMeasurementCalculator.calculate(result);
        if (arDepthMode) arDepthController.updatePose(result);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || resultShown) return;
            overlayView.setResult(result, imageWidth, imageHeight);
            latestPoseMeasurement = poseMeasurement;
            updateStability(resolveMeasurement(poseMeasurement));
        });
    }

    private BodyMeasurement resolveMeasurement(BodyMeasurement poseMeasurement) {
        if (poseMeasurement == null) return null;
        if (arDepthMode) {
            boolean depthIsFresh = latestDepthHeightMeters > 0f
                    && System.currentTimeMillis() - latestDepthHeightAt < 2_500L;
            return depthIsFresh
                    ? BodyMeasurementCalculator.calibratedToHeight(poseMeasurement, latestDepthHeightMeters)
                    : null;
        }
        Float manualHeight = manualHeightMeters();
        return manualHeight == null
                ? null
                : BodyMeasurementCalculator.calibratedToHeight(poseMeasurement, manualHeight);
    }

    private Float manualHeightMeters() {
        try {
            float centimeters = Float.parseFloat(manualHeightInput.getText().toString().trim());
            if (centimeters < 100f || centimeters > 230f) return null;
            return centimeters / 100f;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void updateStability(BodyMeasurement raw) {
        if (raw == null) {
            stableFrames = 0;
            lastRawMeasurement = null;
            measureButton.setEnabled(false);
            measureButton.setText(R.string.body_waiting);
            liveHeightText.setText(R.string.ui_032);
            if (latestPoseMeasurement != null && arDepthMode) {
                liveGuideText.setText(R.string.body_depth_reading);
            } else if (latestPoseMeasurement != null) {
                liveGuideText.setText(R.string.body_height_invalid);
            } else {
                liveGuideText.setText(R.string.body_pose_guide);
            }
            return;
        }

        stableFrames = lastRawMeasurement != null && measurementsAreClose(lastRawMeasurement, raw)
                ? Math.min(STABLE_FRAME_TARGET, stableFrames + 1)
                : 1;
        lastRawMeasurement = raw;
        smoothedMeasurement = blend(smoothedMeasurement, raw, 0.28f);
        liveHeightText.setText(formatCentimeters(smoothedMeasurement.heightMeters));

        if (stableFrames >= STABLE_FRAME_TARGET) {
            measureButton.setEnabled(true);
            measureButton.setText(R.string.body_measure_action);
            liveGuideText.setText(R.string.body_ready);
        } else {
            measureButton.setEnabled(false);
            measureButton.setText(getString(R.string.body_stabilizing, stableFrames, STABLE_FRAME_TARGET));
            liveGuideText.setText(R.string.body_hold_pose);
        }
    }

    private boolean measurementsAreClose(BodyMeasurement first, BodyMeasurement second) {
        return relativeDifference(first.heightMeters, second.heightMeters) < 0.025f
                && relativeDifference(first.shoulderWidthMeters, second.shoulderWidthMeters) < 0.06f
                && relativeDifference(first.hipWidthMeters, second.hipWidthMeters) < 0.06f;
    }

    private float relativeDifference(float first, float second) {
        return Math.abs(first - second) / Math.max(Math.max(first, second), 0.01f);
    }

    private BodyMeasurement blend(BodyMeasurement previous, BodyMeasurement current, float weight) {
        if (previous == null) return current;
        float height = mix(previous.heightMeters, current.heightMeters, weight);
        float shoulder = mix(previous.shoulderWidthMeters, current.shoulderWidthMeters, weight);
        float hip = mix(previous.hipWidthMeters, current.hipWidthMeters, weight);
        float arm = mix(previous.armLengthMeters, current.armLengthMeters, weight);
        float leg = mix(previous.legLengthMeters, current.legLengthMeters, weight);
        String shape = BodyMeasurementCalculator.classifyBodyShape(shoulder, hip);
        return new BodyMeasurement(
                System.currentTimeMillis(), height, shoulder, hip, arm, leg,
                shape, BodyMeasurementCalculator.recommendationFor(shape)
        );
    }

    private float mix(float first, float second, float weight) {
        return first * (1f - weight) + second * weight;
    }

    private void showMeasurementResult() {
        if (smoothedMeasurement == null || stableFrames < STABLE_FRAME_TARGET) return;
        resultShown = true;
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        findViewById(R.id.bodyCaptureContent).setVisibility(View.GONE);
        resultContainer.setVisibility(View.VISIBLE);

        setText(R.id.resultBodyShapeText, smoothedMeasurement.bodyShape);
        setText(R.id.resultHeightText, getString(R.string.body_height_result, formatCentimeters(smoothedMeasurement.heightMeters)));
        setText(R.id.resultShoulderText, getString(R.string.body_shoulders_result, formatCentimeters(smoothedMeasurement.shoulderWidthMeters)));
        setText(R.id.resultHipText, getString(R.string.body_hips_result, formatCentimeters(smoothedMeasurement.hipWidthMeters)));
        setText(R.id.resultArmText, getString(R.string.body_arm_result, formatCentimeters(smoothedMeasurement.armLengthMeters)));
        setText(R.id.resultLegText, getString(R.string.body_leg_result, formatCentimeters(smoothedMeasurement.legLengthMeters)));
        setText(R.id.resultRecommendationText, smoothedMeasurement.recommendation);
    }

    private void resetMeasurement() {
        resultShown = false;
        stableFrames = 0;
        smoothedMeasurement = null;
        lastRawMeasurement = null;
        latestPoseMeasurement = null;
        overlayView.clear();
        resultContainer.setVisibility(View.GONE);
        findViewById(R.id.bodyCaptureContent).setVisibility(View.VISIBLE);
        measureButton.setEnabled(false);
        measureButton.setText(R.string.body_waiting);
        liveHeightText.setText(R.string.ui_032);
        liveGuideText.setText(arDepthMode
                ? getString(R.string.body_ar_guide)
                : getString(R.string.body_manual_reset_guide));
        if (!arDepthMode && imageAnalysis != null) imageAnalysis.setAnalyzer(cameraExecutor, poseService);
    }

    private void saveAndFinish() {
        if (smoothedMeasurement == null) return;
        BodyMeasurement savedMeasurement = smoothedMeasurement.withSyncMetadata(
                arDepthMode ? "ar_raw_depth" : "manual_height",
                arDepthMode ? 0.90f : 0.75f
        );
        BodyMeasurementStorage.save(this, savedMeasurement);
        BodyMeasurementService measurementService = new BodyMeasurementService(this);
        if (measurementService.isConfigured()) {
            new Thread(() -> {
                try {
                    BodyMeasurement synced = measurementService.save(savedMeasurement);
                    BodyMeasurementStorage.save(getApplicationContext(), synced);
                } catch (Exception error) {
                    System.out.println("[BodyMeasureActivity] Measurement sync deferred: " + error.getMessage());
                }
            }).start();
        }
        UiUtils.toast(this, getString(R.string.body_saved));
        finish();
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String formatCentimeters(float meters) {
        return String.format(Locale.getDefault(), "%.1f cm", meters * 100f);
    }

    @Override
    public void onPoseError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(getString(R.string.body_error, message));
        });
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(arPreviewTimeout);
        if (arDepthController != null) arDepthController.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        if (arDepthController != null) arDepthController.close();
        if (poseService != null) poseService.close();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        super.onDestroy();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? getString(R.string.unknown_error) : error.getMessage();
    }

    @Override
    public void onDepthHeight(float heightMeters) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || resultShown) return;
            latestDepthHeightMeters = heightMeters;
            latestDepthHeightAt = System.currentTimeMillis();
            measurementModeText.setText(R.string.body_ar_mode);
            if (latestPoseMeasurement != null) {
                updateStability(resolveMeasurement(latestPoseMeasurement));
            }
        });
    }

    @Override
    public void onArFrameReady() {
        if (!arDepthMode || resultShown) return;
        arFrameReady = true;
        mainHandler.removeCallbacks(arPreviewTimeout);
        statusText.setVisibility(View.GONE);
    }

    @Override
    public void onArFailure(String message) {
        if (isFinishing() || isDestroyed()) return;
        fallbackToManualCamera(message);
    }

    @Override
    public void onDepthStatus(String message) {
        runOnUiThread(() -> {
            if (!resultShown && message != null && !message.trim().isEmpty()) {
                liveGuideText.setText(message);
            }
        });
    }
}
