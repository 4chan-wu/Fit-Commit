package com.fitcommit.app.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;

import com.fitcommit.app.services.BackgroundRemover;
import com.fitcommit.app.utils.UiUtils;

public class AddClothingActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 5101;
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
        new AlertDialog.Builder(this)
                .setTitle("의류 이미지 추가")
                .setMessage("옷 한 벌만 화면 중앙에 놓고 촬영해주세요.\n배경은 단순할수록 분석 정확도가 높아집니다.")
                .setItems(new String[]{"사진 찍기", "갤러리에서 선택"}, (dialog, which) -> {
                    if (which == 0) {
                        openCameraWithPermission();
                    } else {
                        openGallery();
                    }
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void openCameraWithPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            UiUtils.toast(this, "카메라 앱을 찾을 수 없습니다.");
            finish();
            return;
        }
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            UiUtils.toast(this, "카메라 권한이 필요합니다.");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            finish();
            return;
        }

        try {
            String processedPath;
            if (requestCode == REQUEST_PICK_IMAGE) {
                Uri imageUri = data.getData();
                if (imageUri == null) {
                    throw new IllegalStateException("이미지를 선택하지 못했습니다.");
                }
                processedPath = BackgroundRemover.saveOriginalUri(this, imageUri);
            } else if (requestCode == REQUEST_TAKE_PHOTO) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                processedPath = BackgroundRemover.saveOriginalBitmap(this, bitmap);
            } else {
                finish();
                return;
            }

            Intent intent = new Intent(this, AnalysisResultActivity.class);
            intent.putExtra(AnalysisResultActivity.EXTRA_IMAGE_PATH, processedPath);
            intent.putExtra(AnalysisResultActivity.EXTRA_CATEGORY_HINT, selectedCategoryHint);
            startActivity(intent);
        } catch (Exception e) {
            UiUtils.toast(this, "이미지를 처리할 수 없습니다.");
        }
        finish();
    }
}
