package com.fitcommit.app.activities;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
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
    public static final String EXTRA_EDIT_MODE = "editMode";
    public static final String EXTRA_LOCAL_ID = "localId";
    public static final String EXTRA_REMOTE_ID = "remoteId";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_SEASON = "season";
    public static final String EXTRA_NOTE = "note";

    private static final String[] CATEGORIES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private static final String[] COLORS = {"black", "white", "gray", "navy", "blue", "brown", "beige", "red", "pink", "green", "yellow", "purple"};
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
        localItemId = getIntent().getStringExtra(EXTRA_LOCAL_ID);
        serverClothingId = getIntent().getStringExtra(EXTRA_REMOTE_ID);
        if (localItemId == null || localItemId.trim().isEmpty()) {
            localItemId = java.util.UUID.randomUUID().toString();
        }

        findViewById(R.id.resultBackText).setOnClickListener(v -> finish());
        ImageView preview = findViewById(R.id.resultPreviewImage);
        if (imagePath != null && !imagePath.isEmpty()) {
            preview.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        }

        setupStaticForm();
        if (getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false)) {
            setupEditMode();
        } else {
            saveLocalPendingItem();
            registerClothing();
        }
    }

    private void setupStaticForm() {
        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        categorySpinner.setAdapter(adapter);
        int hintIndex = Arrays.asList(CATEGORIES).indexOf(categoryHint);
        categorySpinner.setSelection(Math.max(hintIndex, 0));

        Spinner colorSpinner = findViewById(R.id.colorSpinner);
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLORS);
        colorSpinner.setAdapter(colorAdapter);
        colorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ((EditText) findViewById(R.id.colorEditText)).setText(COLORS[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        findViewById(R.id.editResultButton).setOnClickListener(v -> resetFields());
        findViewById(R.id.saveResultButton).setOnClickListener(v -> saveConfirmedResult());
    }

    private void setupEditMode() {
        result = new AnalysisResult(
                categoryHint,
                "",
                valueOrDefault(getIntent().getStringExtra(EXTRA_COLOR), dominantColorName()),
                valueOrDefault(getIntent().getStringExtra(EXTRA_SEASON), "spring/summer/fall/winter"),
                new String[0],
                0,
                "manual",
                ""
        );
        bindResult(result);
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(valueOrDefault(getIntent().getStringExtra(EXTRA_NAME), defaultNameFor(categoryHint)));
        String note = getIntent().getStringExtra(EXTRA_NOTE);
        if (note != null && note.contains("subcategory=")) {
            ((EditText) findViewById(R.id.subcategoryEditText)).setText(extractNoteValue(note, "subcategory"));
        }
        setLoading(false, "수정 후 저장하면 Backend PUT /clothes/{id}로 반영됩니다.");
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
        String color = normalizeColor(result.mainColor);
        ((TextView) findViewById(R.id.colorSummaryText)).setText("대표 색상  " + color);
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(defaultNameFor(result.mainCategory));
        ((EditText) findViewById(R.id.subcategoryEditText)).setText(result.subcategory);
        ((EditText) findViewById(R.id.colorEditText)).setText(color);
        ((EditText) findViewById(R.id.styleTagsEditText)).setText(joinTags(result.styleTags));
        Spinner colorSpinner = findViewById(R.id.colorSpinner);
        int colorIndex = Arrays.asList(COLORS).indexOf(color);
        if (colorIndex >= 0) {
            colorSpinner.setSelection(colorIndex);
        }

        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        int index = Arrays.asList(CATEGORIES).indexOf(result.mainCategory);
        categorySpinner.setSelection(Math.max(index, 0));

        bindSeason(result.season);
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
        String color = valueOrDefault(((EditText) findViewById(R.id.colorEditText)).getText().toString(), "gray");
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

    private String extractNoteValue(String note, String key) {
        for (String part : note.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring((key + "=").length()).trim();
            }
        }
        return "";
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
                dominantColorName(),
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

    private void bindSeason(String season) {
        ((CheckBox) findViewById(R.id.seasonSpring)).setChecked(false);
        ((CheckBox) findViewById(R.id.seasonSummer)).setChecked(false);
        ((CheckBox) findViewById(R.id.seasonFall)).setChecked(false);
        ((CheckBox) findViewById(R.id.seasonWinter)).setChecked(false);

        String normalized = season == null ? "" : season.toLowerCase();
        if (normalized.contains("all-season")) {
            ((CheckBox) findViewById(R.id.seasonSpring)).setChecked(true);
            ((CheckBox) findViewById(R.id.seasonSummer)).setChecked(true);
            ((CheckBox) findViewById(R.id.seasonFall)).setChecked(true);
            ((CheckBox) findViewById(R.id.seasonWinter)).setChecked(true);
            return;
        }
        if (normalized.contains("spring")) {
            ((CheckBox) findViewById(R.id.seasonSpring)).setChecked(true);
        }
        if (normalized.contains("summer")) {
            ((CheckBox) findViewById(R.id.seasonSummer)).setChecked(true);
        }
        if (normalized.contains("fall")) {
            ((CheckBox) findViewById(R.id.seasonFall)).setChecked(true);
        }
        if (normalized.contains("winter")) {
            ((CheckBox) findViewById(R.id.seasonWinter)).setChecked(true);
        }
        if (!normalized.contains("spring") && !normalized.contains("summer") && !normalized.contains("fall") && !normalized.contains("winter")) {
            ((CheckBox) findViewById(R.id.seasonSpring)).setChecked(true);
            ((CheckBox) findViewById(R.id.seasonSummer)).setChecked(true);
        }
    }

    private String normalizeColor(String color) {
        if (color == null || color.trim().isEmpty() || "#CCCCCC".equalsIgnoreCase(color.trim())) {
            return dominantColorName();
        }
        return color.trim().toLowerCase();
    }

    private String dominantColorName() {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                return "gray";
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            int pixel = scaled.getPixel(0, 0);
            int red = android.graphics.Color.red(pixel);
            int green = android.graphics.Color.green(pixel);
            int blue = android.graphics.Color.blue(pixel);
            String bestName = "gray";
            double bestDistance = Double.MAX_VALUE;
            int[][] values = {
                    {20, 20, 20}, {245, 245, 245}, {140, 140, 140}, {20, 35, 90},
                    {50, 100, 210}, {120, 80, 50}, {210, 190, 150}, {220, 40, 40},
                    {230, 130, 170}, {50, 150, 80}, {230, 210, 50}, {130, 80, 180}
            };
            for (int i = 0; i < COLORS.length; i++) {
                double distance = Math.pow(red - values[i][0], 2)
                        + Math.pow(green - values[i][1], 2)
                        + Math.pow(blue - values[i][2], 2);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestName = COLORS[i];
                }
            }
            return bestName;
        } catch (Exception ignored) {
            return "gray";
        }
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
        StringBuilder builder = new StringBuilder();
        appendSeason(builder, R.id.seasonSpring, "spring");
        appendSeason(builder, R.id.seasonSummer, "summer");
        appendSeason(builder, R.id.seasonFall, "fall");
        appendSeason(builder, R.id.seasonWinter, "winter");
        return builder.length() == 0 ? "spring/summer/fall/winter" : builder.toString();
    }

    private void appendSeason(StringBuilder builder, int viewId, String value) {
        if (((CheckBox) findViewById(viewId)).isChecked()) {
            if (builder.length() > 0) {
                builder.append("/");
            }
            builder.append(value);
        }
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
