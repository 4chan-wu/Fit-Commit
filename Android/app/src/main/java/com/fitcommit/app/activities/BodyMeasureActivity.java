package com.fitcommit.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.opengl.GLSurfaceView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.fitcommit.app.R;
import com.fitcommit.app.models.BodyMeasurement;
import com.fitcommit.app.services.BodyMeasurementCalculator;
import com.fitcommit.app.services.BodyMeasurementStorage;
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
    private static final int CAMERA_PERMISSION_REQUEST = 4201;
    private static final int STABLE_FRAME_TARGET = 8;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_measure);

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
            statusText.setText("신체 분석 모델을 불러오지 못했습니다.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
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
            statusText.setText("AR 측정 기능을 준비하고 있습니다...");
            return;
        }
        if (result == ArDepthMeasurementController.StartResult.ACTIVE) {
            arDepthMode = true;
            findViewById(R.id.bodyCameraPreview).setVisibility(View.GONE);
            findViewById(R.id.bodyArCameraPreview).setVisibility(View.VISIBLE);
            manualHeightInput.setVisibility(View.GONE);
            findViewById(R.id.useManualHeightButton).setVisibility(View.VISIBLE);
            measurementModeText.setText("AR 깊이 기반 실시간 키");
            liveGuideText.setText("휴대폰을 천천히 움직여 바닥을 인식한 뒤 전신을 화면에 맞춰주세요.");
            statusText.setVisibility(View.GONE);
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
        measurementModeText.setText("입력한 키로 신체 비율 보정");
        liveHeightText.setText("--.- cm");
        liveGuideText.setText("정확한 비율 분석을 위해 실제 키를 입력하고 전신이 보이게 서 주세요.");
    }

    private void switchToManualHeightMode() {
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

    private void startCamera() {
        if (cameraXStarted) return;
        cameraXStarted = true;
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("카메라와 신체 분석 모델을 준비하고 있습니다...");
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
                statusText.setText("카메라를 시작할 수 없습니다.\n" + safeMessage(error));
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
            measureButton.setText("전신 인식 대기 중");
            liveHeightText.setText("--.- cm");
            if (latestPoseMeasurement != null && arDepthMode) {
                liveGuideText.setText("깊이를 읽는 중입니다. 휴대폰을 천천히 좌우로 움직여주세요.");
            } else if (latestPoseMeasurement != null) {
                liveGuideText.setText("100~230cm 사이의 실제 키를 입력해주세요.");
            } else {
                liveGuideText.setText("머리부터 발끝, 양손까지 화면 안에 들어오게 서 주세요.");
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
            measureButton.setText("측정 및 분석");
            liveGuideText.setText("측정 준비가 완료됐어요. 자세를 유지하고 버튼을 눌러주세요.");
        } else {
            measureButton.setEnabled(false);
            measureButton.setText("자세 안정화 중 " + stableFrames + "/" + STABLE_FRAME_TARGET);
            liveGuideText.setText("좋아요. 정면을 보고 잠시 같은 자세를 유지해주세요.");
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
        setText(R.id.resultHeightText, "키  ·  " + formatCentimeters(smoothedMeasurement.heightMeters));
        setText(R.id.resultShoulderText, "어깨 너비  ·  " + formatCentimeters(smoothedMeasurement.shoulderWidthMeters));
        setText(R.id.resultHipText, "골반 너비  ·  " + formatCentimeters(smoothedMeasurement.hipWidthMeters));
        setText(R.id.resultArmText, "팔 길이  ·  " + formatCentimeters(smoothedMeasurement.armLengthMeters));
        setText(R.id.resultLegText, "다리 길이  ·  " + formatCentimeters(smoothedMeasurement.legLengthMeters));
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
        measureButton.setText("전신 인식 대기 중");
        liveHeightText.setText("--.- cm");
        liveGuideText.setText(arDepthMode
                ? "휴대폰을 천천히 움직여 바닥을 인식한 뒤 전신을 화면에 맞춰주세요."
                : "실제 키를 입력하고 전신이 화면에 들어오도록 2~3m 떨어져 서 주세요.");
        if (!arDepthMode && imageAnalysis != null) imageAnalysis.setAnalyzer(cameraExecutor, poseService);
    }

    private void saveAndFinish() {
        if (smoothedMeasurement == null) return;
        BodyMeasurementStorage.save(this, smoothedMeasurement);
        UiUtils.toast(this, "신체 분석 결과를 저장했습니다.");
        finish();
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String formatCentimeters(float meters) {
        return String.format(Locale.KOREA, "%.1f cm", meters * 100f);
    }

    @Override
    public void onPoseError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("신체 분석 중 오류가 발생했습니다.\n" + message);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPreferredCamera();
        } else {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("신체 측정을 사용하려면 카메라 권한이 필요합니다.");
        }
    }

    @Override
    protected void onPause() {
        if (arDepthController != null) arDepthController.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        if (arDepthController != null) arDepthController.close();
        if (poseService != null) poseService.close();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        super.onDestroy();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
    }

    @Override
    public void onDepthHeight(float heightMeters) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || resultShown) return;
            latestDepthHeightMeters = heightMeters;
            latestDepthHeightAt = System.currentTimeMillis();
            measurementModeText.setText("AR 깊이 기반 실시간 키");
            if (latestPoseMeasurement != null) {
                updateStability(resolveMeasurement(latestPoseMeasurement));
            }
        });
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
