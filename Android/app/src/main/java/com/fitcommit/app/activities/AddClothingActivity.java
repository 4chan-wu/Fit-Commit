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

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fitcommit.app.R;
import com.fitcommit.app.services.BackgroundRemover;

public class AddClothingActivity extends ComponentActivity {
    public static final String EXTRA_WARDROBE_ID = "wardrobeId";
    private static final int REQUEST_TAKE_PHOTO = 5102;
    private static final int REQUEST_PICK_IMAGE = 5103;
    private static final String[] CATEGORY_VALUES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private String selectedCategoryHint = CATEGORY_VALUES[0];
    private String selectedWardrobeId = "";
    private AlertDialog progressDialog;
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private int pendingRequestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleActivityResult(pendingRequestCode, result.getResultCode(), result.getData())
        );
        selectedWardrobeId = getIntent().getStringExtra(EXTRA_WARDROBE_ID);
        if (selectedWardrobeId == null) selectedWardrobeId = "";
        showCategoryDialog();
    }

    private void showCategoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_item_type_title)
                .setSingleChoiceItems(R.array.clothing_category_labels, 0, (dialog, which) -> selectedCategoryHint = CATEGORY_VALUES[which])
                .setPositiveButton(R.string.action_next, (dialog, which) -> showSourceDialog())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> finish())
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
        pendingRequestCode = REQUEST_TAKE_PHOTO;
        activityResultLauncher.launch(intent);
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
        pendingRequestCode = REQUEST_PICK_IMAGE;
        activityResultLauncher.launch(intent);
    }

    private void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            showSourceDialog();
            return;
        }

        showProcessingDialog(getString(R.string.source_preparing));
        if (requestCode == REQUEST_PICK_IMAGE) {
            Uri imageUri = data.getData();
            if (imageUri == null) {
                showProcessingError(getString(R.string.image_select_failed));
                return;
            }
            new Thread(() -> {
                try {
                    String originalPath = BackgroundRemover.saveOriginalUri(this, imageUri);
                    runOnUiThread(() -> removeBackground(originalPath));
                } catch (Exception error) {
                    runOnUiThread(() -> showProcessingError(getString(R.string.image_read_failed, safeMessage(error))));
                }
            }).start();
            return;
        }

        if (requestCode == REQUEST_TAKE_PHOTO) {
            String originalPath = data.getStringExtra(ClothingCameraActivity.EXTRA_CAPTURED_IMAGE_PATH);
            if (originalPath == null || originalPath.trim().isEmpty()) {
                showProcessingError(getString(R.string.capture_load_failed));
                return;
            }
            new Thread(() -> {
                try {
                    String normalizedPath = BackgroundRemover.normalizeCapturedFile(this, originalPath);
                    runOnUiThread(() -> removeBackground(normalizedPath));
                } catch (Exception error) {
                    runOnUiThread(() -> showProcessingError(
                            getString(R.string.capture_prepare_failed, safeMessage(error))
                    ));
                }
            }).start();
            return;
        }
        dismissProgressDialog();
        finish();
    }

    private void removeBackground(String originalPath) {
        showProcessingDialog(getString(R.string.background_removing));
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
        intent.putExtra(AnalysisResultActivity.EXTRA_WARDROBE_ID, selectedWardrobeId);
        startActivity(intent);
        finish();
    }

    private void showBackgroundRemovalFallback(String originalPath, String message) {
        dismissProgressDialog();
        new AlertDialog.Builder(this)
                .setTitle(R.string.background_difficult_title)
                .setMessage(getString(R.string.background_difficult_message, message))
                .setPositiveButton(R.string.use_original, (dialog, which) ->
                        openAnalysisResult(originalPath, originalPath, false))
                .setNeutralButton(R.string.action_retry, (dialog, which) -> showSourceDialog())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showProcessingDialog(String message) {
        dismissProgressDialog();
        progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.photo_preparing_title)
                .setMessage(message)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void showProcessingError(String message) {
        dismissProgressDialog();
        new AlertDialog.Builder(this)
                .setTitle(R.string.background_failed_title)
                .setMessage(message)
                .setPositiveButton(R.string.action_retry, (dialog, which) -> showSourceDialog())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? getString(R.string.unknown_error) : error.getMessage();
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        super.onDestroy();
    }
}
