package com.fitcommit.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.AnalysisResult;
import com.fitcommit.app.services.FashionAiService;
import com.fitcommit.app.utils.UiUtils;

/**
 * iOS AnalysisResultView에 대응하는 분석 결과 편집 화면입니다.
 * AI 서버 연동 전까지는 FashionAiService의 샘플 결과로 화면을 구성합니다.
 */
public class AnalysisResultActivity extends Activity {
    private final FashionAiService fashionAiService = new FashionAiService();
    private AnalysisResult result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);

        result = fashionAiService.analyzeSample();
        setupResultForm();
    }

    private void setupResultForm() {
        findViewById(R.id.resultBackText).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.resultCategoryText)).setText(result.category);
        ((TextView) findViewById(R.id.resultConfidenceText)).setText(Math.round(result.categoryConfidence * 100) + "% confidence");
        ((TextView) findViewById(R.id.colorSummaryText)).setText("Color  " + result.color);
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(result.category);
        ((EditText) findViewById(R.id.colorEditText)).setText(result.color);

        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        String[] categories = {"T-shirt", "Shirt", "Hoodie", "Sweater", "Pants", "Skirt", "Dress", "Outerwear"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(adapter);

        RadioButton spring = findViewById(R.id.seasonSpring);
        RadioButton fall = findViewById(R.id.seasonFall);
        if ("Fall/Winter".equals(result.season)) {
            fall.setChecked(true);
        } else {
            spring.setChecked(true);
        }

        findViewById(R.id.saveResultButton).setOnClickListener(v -> {
            // TODO: 분석 결과를 로컬 DB 또는 Backend 옷장 API에 저장
            UiUtils.toast(this, "의류가 저장되었습니다.");
            finish();
        });
    }
}
