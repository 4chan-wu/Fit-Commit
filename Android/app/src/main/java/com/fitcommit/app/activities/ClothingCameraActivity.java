package com.fitcommit.app.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.fitcommit.app.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

public class ClothingCameraActivity extends ComponentActivity {
    public static final String EXTRA_CAPTURED_IMAGE_PATH = "capturedImagePath";
    private static final int CAMERA_PERMISSION_REQUEST = 5201;

    private PreviewView previewView;
    private TextView statusText;
    private Button captureButton;
    private ImageCapture imageCapture;
    private OrientationEventListener orientationListener;
    private boolean cameraReady;
    private boolean capturing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clothing_camera);

        previewView = findViewById(R.id.clothingCameraPreview);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        statusText = findViewById(R.id.clothingCameraStatusText);
        captureButton = findViewById(R.id.captureClothingButton);
        findViewById(R.id.clothingCameraBackText).setOnClickListener(v -> finish());
        captureButton.setOnClickListener(v -> capturePhoto());
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (!cameraReady || capturing || orientation == ORIENTATION_UNKNOWN) return;
                if (imageCapture != null) {
                    imageCapture.setTargetRotation(captureRotationFor(orientation));
                }
                boolean landscape = (orientation >= 60 && orientation <= 120)
                        || (orientation >= 240 && orientation <= 300);
                statusText.setVisibility(landscape ? View.VISIBLE : View.GONE);
                if (landscape) {
                    statusText.setText("옷 전체가 잘 보이도록 휴대전화를 세로로 세워주세요.");
                }
            }
        };

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void startCamera() {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("카메라를 준비하고 있습니다...");
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(95)
                        .setTargetRotation(currentRotation())
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                cameraReady = true;
                statusText.setVisibility(View.GONE);
                captureButton.setEnabled(true);
            } catch (Exception error) {
                showError("카메라를 시작할 수 없습니다. " + safeMessage(error));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        capturing = true;
        captureButton.setEnabled(false);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("고해상도 사진을 저장하고 있습니다...");

        File directory = new File(getFilesDir(), "closet_originals");
        if (!directory.exists() && !directory.mkdirs()) {
            showError("촬영 이미지를 저장할 폴더를 만들 수 없습니다.");
            return;
        }
        File outputFile = new File(directory, "original_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(outputFile).build();
        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        Intent result = new Intent();
                        result.putExtra(EXTRA_CAPTURED_IMAGE_PATH, outputFile.getAbsolutePath());
                        setResult(Activity.RESULT_OK, result);
                        finish();
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        showError("사진 촬영에 실패했습니다. " + safeMessage(exception));
                    }
                }
        );
    }

    private int currentRotation() {
        return previewView.getDisplay() == null ? Surface.ROTATION_0 : previewView.getDisplay().getRotation();
    }

    private int captureRotationFor(int orientation) {
        if (orientation >= 45 && orientation < 135) return Surface.ROTATION_270;
        if (orientation >= 135 && orientation < 225) return Surface.ROTATION_180;
        if (orientation >= 225 && orientation < 315) return Surface.ROTATION_90;
        return Surface.ROTATION_0;
    }

    private void showError(String message) {
        capturing = false;
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
        captureButton.setEnabled(imageCapture != null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orientationListener != null && orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    @Override
    protected void onPause() {
        if (orientationListener != null) orientationListener.disable();
        super.onPause();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            showError("옷을 촬영하려면 카메라 권한이 필요합니다.");
        }
    }
}
