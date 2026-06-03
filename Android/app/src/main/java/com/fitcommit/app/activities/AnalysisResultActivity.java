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
import com.fitcommit.app.services.ApiClient;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.services.ClothesBackendService;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.services.FashionAiService;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.Arrays;

/**
 * CLIP/FashionCLIP 분석 결과를 확정 전 확인하고 수정하는 화면입니다.
 */
public class AnalysisResultActivity extends Activity {
    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_CATEGORY_HINT = "categoryHint";

    private static final String[] CATEGORIES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private final FashionAiService fashionAiService = new FashionAiService();
    private final ClothesBackendService clothesBackendService = new ClothesBackendService();

    private String imagePath;
    private String categoryHint;
    private String localItemId;
    private String serverClothingId;
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
        saveLocalPendingItem();
        registerClothing();
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

    private void registerClothing() {
        setLoading(true, "Backend /clothes 저장을 먼저 진행 중입니다...");

        new Thread(() -> {
            try {
                if (imagePath == null || imagePath.trim().isEmpty()) {
                    throw new IllegalStateException("이미지 파일이 없습니다.");
                }
                ensurePendingClothingRow();

                if (!ApiClient.ENABLE_AI_ANALYSIS) {
                    System.out.println("[AnalysisResultActivity] AI analysis skipped ENABLE_AI_ANALYSIS=false");
                    runOnUiThread(() -> {
                        result = AnalysisResult.fallback(categoryHint);
                        bindResult(result);
                        setLoading(false, "저장 완료. AI 분석은 나중에 다시 분석하기로 진행할 수 있습니다.");
                        UiUtils.toast(this, serverClothingId == null || serverClothingId.trim().isEmpty()
                                ? "기기에 저장되었습니다."
                                : "Backend에 의류가 저장되었습니다.");
                        finish();
                    });
                    return;
                }

                System.out.println("[AnalysisResultActivity] AI analysis attempt started");
                AnalysisResult analyzed = fashionAiService.analyze(new File(imagePath), categoryHint);
                System.out.println("[AnalysisResultActivity] AI analysis succeeded");
                updateAnalysisCompleted(analyzed);
                runOnUiThread(() -> {
                    result = analyzed;
                    bindResult(analyzed);
                    setLoading(false, "");
                });
            } catch (Exception error) {
                System.out.println("[AnalysisResultActivity] AI analysis failed: " + error.getMessage());
                updateAnalysisFailed(error.getMessage());
                runOnUiThread(() -> {
                    result = AnalysisResult.fallback(categoryHint);
                    bindResult(result);
                    setLoading(false, "분석 실패 상태로 저장되었습니다. 나중에 다시 분석할 수 있습니다.");
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

        saveLocally(name, category, subcategory, color, season, styleTags, false);

        if (clothesBackendService.isConfigured()) {
            setLoading(true, "서버에 의류 정보를 저장 중입니다...");
            new Thread(() -> {
                try {
                    if (serverClothingId == null || serverClothingId.trim().isEmpty()) {
                        org.json.JSONObject row = clothesBackendService.createCompleted(category, subcategory, color, season, styleTags, imagePath, result);
                        Object id = row.opt("id");
                        serverClothingId = id == null ? null : String.valueOf(id);
                    } else {
                        clothesBackendService.updateCompleted(serverClothingId, category, subcategory, color, season, styleTags, imagePath, result);
                    }
                    ClothingStorage.updateSync(this, localItemId, serverClothingId, "synced", "");
                    runOnUiThread(() -> {
                        UiUtils.toast(this, "서버에 의류가 저장되었습니다.");
                        finish();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        System.out.println("[AnalysisResultActivity] Backend save failed, local item retained: " + error.getMessage());
                        ClothingStorage.updateSync(this, localItemId, serverClothingId, "sync_failed", error.getMessage());
                        UiUtils.toast(this, "서버 저장 실패, 기기에 임시 저장되었습니다.");
                        finish();
                    });
                }
            }).start();
            return;
        }

        UiUtils.toast(this, "기기에 임시 저장되었습니다.");
        finish();
    }

    private void saveLocally(
            String name,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            boolean finishAfterSave
    ) {
        String note = "subcategory=" + subcategory + "; style_tags=" + styleTags;
        ClosetItem current = ClothingStorage.findByLocalId(this, localItemId);
        String syncStatus = current == null ? "local_only" : current.syncStatus;
        String syncError = current == null ? "" : current.syncError;
        if (serverClothingId != null && !serverClothingId.trim().isEmpty()) {
            syncStatus = "synced";
            syncError = "";
        }
        ClothingStorage.saveOrReplace(this, new ClosetItem(
                localItemId,
                serverClothingId,
                name,
                category,
                color,
                season,
                note,
                imagePath,
                result == null ? "pending" : result.confidence > 0 ? "completed" : "failed",
                result == null ? "" : "",
                result == null || result.confidence <= 0,
                syncStatus,
                syncError,
                System.currentTimeMillis()
        ));
        if (finishAfterSave) {
            UiUtils.toast(this, "확정한 의류 정보가 기기에 저장되었습니다.");
            finish();
        }
    }

    private void saveLocalPendingItem() {
        ClosetItem item = new ClosetItem(
                java.util.UUID.randomUUID().toString(),
                "",
                defaultNameFor(categoryHint),
                categoryHint,
                "#CCCCCC",
                "분석 대기",
                "created_before_ai_analysis",
                imagePath,
                "pending",
                "",
                true,
                "pending_sync",
                "",
                System.currentTimeMillis()
        );
        localItemId = item.localId;
        ClothingStorage.saveOrReplace(this, item);
        System.out.println("[AnalysisResultActivity] Local pending item saved id=" + localItemId);
    }

    private void ensurePendingClothingRow() {
        if (serverClothingId != null && !serverClothingId.trim().isEmpty()) {
            return;
        }
        if (!clothesBackendService.isConfigured()) {
            System.out.println("[AnalysisResultActivity] Backend save skipped: Backend config missing");
            ClothingStorage.updateSync(this, localItemId, "", "local_only", "");
            return;
        }
        try {
            System.out.println("[AnalysisResultActivity] Backend /clothes request started");
            org.json.JSONObject row = clothesBackendService.createPending(categoryHint, imagePath);
            Object id = row.opt("id");
            serverClothingId = id == null ? null : String.valueOf(id);
            ClothingStorage.updateSync(this, localItemId, serverClothingId, "synced", "");
            System.out.println("[AnalysisResultActivity] Backend save succeeded id=" + serverClothingId);
        } catch (Exception error) {
            ClothingStorage.updateSync(this, localItemId, "", "sync_failed", error.getMessage());
            System.out.println("[AnalysisResultActivity] Backend save failed, local item retained: " + error.getMessage());
        }
    }

    private void updateLocalAnalysisCompleted(AnalysisResult analyzed) {
        ClosetItem current = ClothingStorage.findByLocalId(this, localItemId);
        String syncStatus = current == null ? "local_only" : current.syncStatus;
        String syncError = current == null ? "" : current.syncError;
        if (serverClothingId != null && !serverClothingId.trim().isEmpty()) {
            syncStatus = "synced";
            syncError = "";
        }
        ClothingStorage.updateAnalysis(this, localItemId, new ClosetItem(
                localItemId,
                serverClothingId,
                analyzed.subcategory == null || analyzed.subcategory.isEmpty() ? analyzed.mainCategory : analyzed.subcategory,
                analyzed.mainCategory,
                analyzed.mainColor,
                analyzed.season,
                "analysis_method=" + analyzed.method,
                imagePath,
                "completed",
                "",
                false,
                syncStatus,
                syncError,
                System.currentTimeMillis()
        ));
    }

    private void updateLocalAnalysisFailed(String message) {
        ClosetItem current = ClothingStorage.findByLocalId(this, localItemId);
        String syncStatus = current == null ? "local_only" : current.syncStatus;
        String syncError = current == null ? "" : current.syncError;
        ClothingStorage.updateAnalysis(this, localItemId, new ClosetItem(
                localItemId,
                serverClothingId,
                defaultNameFor(categoryHint),
                categoryHint,
                "#CCCCCC",
                "분석 실패",
                "retry_available=true",
                imagePath,
                "failed",
                message == null ? "AI analysis failed" : message,
                true,
                syncStatus,
                syncError,
                System.currentTimeMillis()
        ));
    }

    private void updateAnalysisCompleted(AnalysisResult analyzed) {
        updateLocalAnalysisCompleted(analyzed);
        if (serverClothingId == null || serverClothingId.trim().isEmpty()) {
            return;
        }
        try {
            clothesBackendService.updateCompleted(
                    serverClothingId,
                    analyzed.mainCategory,
                    analyzed.subcategory,
                    analyzed.mainColor,
                    analyzed.season,
                    joinTags(analyzed.styleTags),
                    imagePath,
                    analyzed
            );
        } catch (Exception error) {
            System.out.println("[AnalysisResultActivity] Failed to mark completed: " + error.getMessage());
        }
    }

    private void updateAnalysisFailed(String message) {
        updateLocalAnalysisFailed(message);
        if (serverClothingId == null || serverClothingId.trim().isEmpty()) {
            return;
        }
        try {
            clothesBackendService.updateFailed(serverClothingId, imagePath, message);
        } catch (Exception updateError) {
            System.out.println("[AnalysisResultActivity] Failed to mark failed: " + updateError.getMessage());
        }
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
