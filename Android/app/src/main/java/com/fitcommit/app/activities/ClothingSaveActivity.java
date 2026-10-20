package com.fitcommit.app.activities;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.utils.UiUtils;

public class ClothingSaveActivity extends Activity {
    public static final String EXTRA_IMAGE_PATH = "imagePath";

    private String imagePath;
    private EditText nameEditText;
    private EditText categoryEditText;
    private EditText colorEditText;
    private EditText memoEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clothing_save);

        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        nameEditText = findViewById(R.id.clothingNameEditText);
        categoryEditText = findViewById(R.id.categoryEditText);
        colorEditText = findViewById(R.id.colorEditText);
        memoEditText = findViewById(R.id.memoEditText);

        ImageView preview = findViewById(R.id.clothingPreviewImage);
        if (imagePath != null && !imagePath.isEmpty()) {
            preview.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        }

        categoryEditText.setText(getString(R.string.new_top));
        colorEditText.setText(getString(R.string.analysis_planned));

        findViewById(R.id.saveBackText).setOnClickListener(v -> finish());
        findViewById(R.id.saveClothingButton).setOnClickListener(v -> saveClothing());
    }

    private void saveClothing() {
        String name = valueOrDefault(nameEditText.getText().toString(), getString(R.string.new_clothing));
        String category = valueOrDefault(categoryEditText.getText().toString(), getString(R.string.new_top));
        String color = valueOrDefault(colorEditText.getText().toString(), getString(R.string.analysis_planned));
        String memo = valueOrDefault(memoEditText.getText().toString(), getString(R.string.ai_analysis_planned));

        ClothingStorage.save(this, new ClosetItem(name, category, color, getString(R.string.registered), memo, imagePath));
        UiUtils.toast(this, getString(R.string.clothing_saved));
        finish();
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
