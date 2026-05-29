package com.fitcommit.app.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.fitcommit.app.services.BackgroundRemover;
import com.fitcommit.app.utils.UiUtils;

public class AddClothingActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 5101;
    private static final int REQUEST_TAKE_PHOTO = 5102;
    private static final int REQUEST_PICK_IMAGE = 5103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSourceDialog();
    }

    private void showSourceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("의류 이미지 추가")
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
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
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
                processedPath = BackgroundRemover.processUri(this, imageUri);
            } else if (requestCode == REQUEST_TAKE_PHOTO) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                processedPath = BackgroundRemover.processBitmap(this, bitmap);
            } else {
                finish();
                return;
            }

            // TODO: AI 분석 기능 연동 예정
            // analyzeClothingWithAI(removedBackgroundImage);
            Intent intent = new Intent(this, ClothingSaveActivity.class);
            intent.putExtra(ClothingSaveActivity.EXTRA_IMAGE_PATH, processedPath);
            startActivity(intent);
        } catch (Exception e) {
            UiUtils.toast(this, "이미지를 처리할 수 없습니다.");
        }
        finish();
    }
}
