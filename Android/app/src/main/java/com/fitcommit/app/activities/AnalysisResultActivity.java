package com.fitcommit.app.activities;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.AnalysisResult;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.services.FashionAiService;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.Arrays;

/**
 * CLIP/FashionCLIP 분석 결과를 저장 전 확인하고 수정하는 화면입니다.
 */
public class AnalysisResultActivity extends Activity {
    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_CATEGORY_HINT = "categoryHint";

    private static final String[] CATEGORIES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private final FashionAiService fashionAiService = new FashionAiService();

    private String imagePath;
    private String categoryHint;
    private AnalysisResult result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        categoryHint = valueOrDefault(getIntent().getStringExtra(EXTRA_CATEGORY_HINT), "top");

        findViewById(R.id.resultBackText).setOnClickListener(v -> finish());
        ImageView preview = findViewById(R.id.resultPreviewImage);
        if (imagePath != null && !imagePath.isEmpty()) {
            preview.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        }

        setupStaticForm();
        analyzeImage();
    }

    private void setupStaticForm() {
        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        categorySpinner.setAdapter(adapter);
        int hintIndex = Arrays.asList(CATEGORIES).indexOf(categoryHint);
        categorySpinner.setSelection(Math.max(hintIndex, 0));

        findViewById(R.id.editResultButton).setOnClickListener(v -> resetFields());
        findViewById(R.id.saveResultButton).setOnClickListener(v -> saveConfirmedResult());
    }

    private void analyzeImage() {
        setLoading(true, "AI가 아이템 단독 사진을 분석 중입니다...");

        new Thread(() -> {
            try {
                if (imagePath == null || imagePath.trim().isEmpty()) {
                    throw new IllegalStateException("이미지 파일이 없습니다.");
                }
                AnalysisResult analyzed = fashionAiService.analyze(new File(imagePath), categoryHint);
                runOnUiThread(() -> {
                    result = analyzed;
                    bindResult(analyzed);
                    setLoading(false, "");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    result = AnalysisResult.fallback(categoryHint);
                    bindResult(result);
                    setLoading(false, "서버 분석에 실패해 선택한 카테고리로 수동 저장할 수 있습니다.");
                });
            }
        }).start();
    }

    private void bindResult(AnalysisResult result) {
        ((TextView) findViewById(R.id.resultCategoryText)).setText(result.mainCategory);
        ((TextView) findViewById(R.id.resultConfidenceText)).setText(Math.round(result.confidence * 100) + "% confidence");
        ((TextView) findViewById(R.id.colorSummaryText)).setText("대표 색상  " + result.mainColor);
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(defaultNameFor(result.mainCategory));
        ((EditText) findViewById(R.id.subcategoryEditText)).setText(result.subcategory);
        ((EditText) findViewById(R.id.colorEditText)).setText(result.mainColor);
        ((EditText) findViewById(R.id.styleTagsEditText)).setText(joinTags(result.styleTags));

        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        int index = Arrays.asList(CATEGORIES).indexOf(result.mainCategory);
        categorySpinner.setSelection(Math.max(index, 0));

        RadioButton spring = findViewById(R.id.seasonSpring);
        RadioButton fall = findViewById(R.id.seasonFall);
        RadioButton all = findViewById(R.id.seasonAll);
        if ("Fall/Winter".equals(result.season)) {
            fall.setChecked(true);
        } else if ("All-Season".equals(result.season)) {
            all.setChecked(true);
        } else {
            spring.setChecked(true);
        }
    }

    private void resetFields() {
        if (result != null) {
            bindResult(result);
        }
    }

    private void saveConfirmedResult() {
        String name = valueOrDefault(((EditText) findViewById(R.id.clothingNameEditText)).getText().toString(), "새 의류");
        String category = ((Spinner) findViewById(R.id.categorySpinner)).getSelectedItem().toString();
        String subcategory = ((EditText) findViewById(R.id.subcategoryEditText)).getText().toString().trim();
        String color = valueOrDefault(((EditText) findViewById(R.id.colorEditText)).getText().toString(), "#CCCCCC");
        String styleTags = ((EditText) findViewById(R.id.styleTagsEditText)).getText().toString().trim();
        String season = selectedSeason();

        String note = "subcategory=" + subcategory + "; style_tags=" + styleTags;
        ClothingStorage.save(this, new ClosetItem(name, category, color, season, note, imagePath));
        UiUtils.toast(this, "확정한 의류 정보가 저장되었습니다.");
        finish();
    }

    private String selectedSeason() {
        if (((RadioButton) findViewById(R.id.seasonFall)).isChecked()) {
            return "Fall/Winter";
        }
        if (((RadioButton) findViewById(R.id.seasonAll)).isChecked()) {
            return "All-Season";
        }
        return "Spring/Summer";
    }

    private void setLoading(boolean loading, String message) {
        TextView statusText = findViewById(R.id.analysisStatusText);
        statusText.setText(message);
        statusText.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.saveResultButton).setEnabled(!loading);
        findViewById(R.id.editResultButton).setEnabled(!loading);
    }

    private String defaultNameFor(String category) {
        switch (category) {
            case "top":
                return "새로운 상의";
            case "bottom":
                return "새로운 하의";
            case "outer":
                return "새로운 아우터";
            case "shoes":
                return "새로운 신발";
            case "hat":
                return "새로운 모자";
            case "bag":
                return "새로운 가방";
            case "dress":
                return "새로운 원피스";
            default:
                return "새 의류";
        }
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String joinTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(tag.trim());
        }
        return builder.toString();
    }
}
