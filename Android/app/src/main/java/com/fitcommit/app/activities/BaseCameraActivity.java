package com.fitcommit.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.fitcommit.app.R;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class BaseCameraActivity extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 4101;

    private PreviewView previewView;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        previewView = findViewById(R.id.cameraPreview);
        statusText = findViewById(R.id.cameraStatusText);

        TextView backText = findViewById(R.id.cameraBackText);
        backText.setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.cameraTitleText)).setText(getScreenTitle());
        ((TextView) findViewById(R.id.cameraGuideText)).setText(getGuideText());
        ((TextView) findViewById(R.id.analysisTitleText)).setText(getAnalysisTitle());
        ((TextView) findViewById(R.id.analysisBodyText)).setText(getAnalysisBody());

        if (hasCameraPermission()) {
            startCamera();
        } else {
            statusText.setText("카메라 권한이 필요합니다.");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        statusText.setText("카메라를 준비하고 있습니다...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                statusText.setVisibility(View.GONE);
                onCameraReady();
            } catch (Exception e) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText("카메라를 시작할 수 없습니다.\n" + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusText.setText("카메라 권한이 없어 기능을 사용할 수 없습니다.");
        }
    }

    protected void onCameraReady() {
    }

    protected abstract String getScreenTitle();

    protected abstract String getGuideText();

    protected abstract String getAnalysisTitle();

    protected abstract String getAnalysisBody();
}
