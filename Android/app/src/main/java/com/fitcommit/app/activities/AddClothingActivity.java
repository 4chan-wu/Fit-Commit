package com.fitcommit.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;

import com.fitcommit.app.R;
import com.fitcommit.app.services.BackgroundRemover;

public class AddClothingActivity extends Activity {
    private static final int REQUEST_TAKE_PHOTO = 5102;
    private static final int REQUEST_PICK_IMAGE = 5103;
    private static final String[] CATEGORY_VALUES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private static final String[] CATEGORY_LABELS = {
            "상의 (top)",
            "하의 (bottom)",
            "아우터 (outer)",
            "신발 (shoes)",
            "모자 (hat)",
            "가방 (bag)",
            "원피스 (dress)"
    };

    private String selectedCategoryHint = CATEGORY_VALUES[0];
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showCategoryDialog();
    }

    private void showCategoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("아이템 종류 선택")
                .setSingleChoiceItems(CATEGORY_LABELS, 0, (dialog, which) -> selectedCategoryHint = CATEGORY_VALUES[which])
                .setPositiveButton("다음", (dialog, which) -> showSourceDialog())
                .setNegativeButton("취소", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showSourceDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_clothing_source, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setOnCancelListener(ignored -> finish())
                .create();
        content.findViewById(R.id.takeClothingPhotoButton).setOnClickListener(view -> {
            dialog.dismiss();
            openCamera();
        });
        content.findViewById(R.id.pickClothingPhotoButton).setOnClickListener(view -> {
            dialog.dismiss();
            openGallery();
        });
        content.findViewById(R.id.cancelClothingSourceButton).setOnClickListener(view -> {
            dialog.dismiss();
            finish();
        });
        dialog.show();
    }

    private void openCamera() {
        Intent intent = new Intent(this, ClothingCameraActivity.class);
        startActivityForResult(intent, REQUEST_TAKE_PHOTO);
    }

    private void openGallery() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            showSourceDialog();
            return;
        }

        showProcessingDialog("원본 이미지를 준비하고 있습니다...");
        if (requestCode == REQUEST_PICK_IMAGE) {
            Uri imageUri = data.getData();
            if (imageUri == null) {
                showProcessingError("이미지를 선택하지 못했습니다.");
                return;
            }
            new Thread(() -> {
                try {
                    String originalPath = BackgroundRemover.saveOriginalUri(this, imageUri);
                    runOnUiThread(() -> removeBackground(originalPath));
                } catch (Exception error) {
                    runOnUiThread(() -> showProcessingError("이미지를 읽을 수 없습니다. " + safeMessage(error)));
                }
            }).start();
            return;
        }

        if (requestCode == REQUEST_TAKE_PHOTO) {
            String originalPath = data.getStringExtra(ClothingCameraActivity.EXTRA_CAPTURED_IMAGE_PATH);
            if (originalPath == null || originalPath.trim().isEmpty()) {
                showProcessingError("촬영한 이미지를 불러오지 못했습니다.");
                return;
            }
            removeBackground(originalPath);
            return;
        }
        dismissProgressDialog();
        finish();
    }

    private void removeBackground(String originalPath) {
        showProcessingDialog("기기에서 옷과 배경을 분리하고 있습니다...");
        BackgroundRemover.processFile(this, originalPath, new BackgroundRemover.Callback() {
            @Override
            public void onSuccess(String processedImagePath) {
                runOnUiThread(() -> openAnalysisResult(originalPath, processedImagePath, true));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showBackgroundRemovalFallback(originalPath, message));
            }
        });
    }

    private void openAnalysisResult(String originalPath, String displayImagePath, boolean backgroundRemoved) {
        dismissProgressDialog();
        Intent intent = new Intent(this, AnalysisResultActivity.class);
        intent.putExtra(AnalysisResultActivity.EXTRA_IMAGE_PATH, displayImagePath);
        intent.putExtra(AnalysisResultActivity.EXTRA_ORIGINAL_IMAGE_PATH, originalPath);
        intent.putExtra(AnalysisResultActivity.EXTRA_BACKGROUND_REMOVED, backgroundRemoved);
        intent.putExtra(AnalysisResultActivity.EXTRA_CATEGORY_HINT, selectedCategoryHint);
        startActivity(intent);
        finish();
    }

    private void showBackgroundRemovalFallback(String originalPath, String message) {
        dismissProgressDialog();
        new AlertDialog.Builder(this)
                .setTitle("배경 제거가 어려운 사진이에요")
                .setMessage(message + "\n\n촬영한 원본 사진은 그대로 등록할 수 있습니다.")
                .setPositiveButton("원본으로 등록", (dialog, which) ->
                        openAnalysisResult(originalPath, originalPath, false))
                .setNeutralButton("다시 선택", (dialog, which) -> showSourceDialog())
                .setNegativeButton("취소", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showProcessingDialog(String message) {
        dismissProgressDialog();
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("옷 사진 준비 중")
                .setMessage(message)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void showProcessingError(String message) {
        dismissProgressDialog();
        new AlertDialog.Builder(this)
                .setTitle("배경을 제거하지 못했어요")
                .setMessage(message)
                .setPositiveButton("다시 선택", (dialog, which) -> showSourceDialog())
                .setNegativeButton("취소", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        super.onDestroy();
    }
}
