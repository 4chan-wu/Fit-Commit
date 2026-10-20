package com.fitcommit.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
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
import com.fitcommit.app.services.BackgroundRemover;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.models.Wardrobe;
import com.fitcommit.app.services.ClothesBackendService;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.services.FashionAiService;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CLIP/FashionCLIP 분석 결과를 확정 전 확인하고 수정하는 화면입니다.
 */
public class AnalysisResultActivity extends Activity {
    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_ORIGINAL_IMAGE_PATH = "originalImagePath";
    public static final String EXTRA_BACKGROUND_REMOVED = "backgroundRemoved";
    public static final String EXTRA_CATEGORY_HINT = "categoryHint";
    public static final String EXTRA_EDIT_MODE = "editMode";
    public static final String EXTRA_LOCAL_ID = "localId";
    public static final String EXTRA_REMOTE_ID = "remoteId";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_SEASON = "season";
    public static final String EXTRA_NOTE = "note";
    public static final String EXTRA_WARDROBE_ID = "wardrobeId";

    private static final String[] CATEGORIES = {"top", "bottom", "outer", "shoes", "hat", "bag", "dress"};
    private static final String[] COLORS = {"black", "white", "gray", "navy", "blue", "brown", "beige", "red", "pink", "green", "yellow", "purple"};
    private String[] categoryLabels;
    private String[] colorLabels;
    private final FashionAiService fashionAiService = new FashionAiService();
    private ClothesBackendService clothesBackendService;

    private String imagePath;
    private String originalImagePath;
    private boolean backgroundRemoved;
    private String categoryHint;
    private String localItemId;
    private String serverClothingId;
    private String selectedWardrobeId = "";
    private final List<Wardrobe> wardrobes = new ArrayList<>();
    private AnalysisResult result;
    private boolean detailsVisible;
    private boolean wardrobesReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);
        clothesBackendService = new ClothesBackendService(this);
        categoryLabels = getResources().getStringArray(R.array.clothing_category_labels);
        colorLabels = getResources().getStringArray(R.array.clothing_color_labels);

        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        originalImagePath = valueOrDefault(
                getIntent().getStringExtra(EXTRA_ORIGINAL_IMAGE_PATH),
                imagePath
        );
        backgroundRemoved = getIntent().hasExtra(EXTRA_BACKGROUND_REMOVED)
                ? getIntent().getBooleanExtra(EXTRA_BACKGROUND_REMOVED, false)
                : imagePath != null && originalImagePath != null && !imagePath.equals(originalImagePath);
        categoryHint = valueOrDefault(getIntent().getStringExtra(EXTRA_CATEGORY_HINT), "top");
        localItemId = getIntent().getStringExtra(EXTRA_LOCAL_ID);
        serverClothingId = getIntent().getStringExtra(EXTRA_REMOTE_ID);
        selectedWardrobeId = valueOrDefault(getIntent().getStringExtra(EXTRA_WARDROBE_ID), "");
        if (localItemId == null || localItemId.trim().isEmpty()) {
            localItemId = java.util.UUID.randomUUID().toString();
        }

        findViewById(R.id.resultBackText).setOnClickListener(v -> finish());
        ImageView preview = findViewById(R.id.resultPreviewImage);
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                preview.setImageBitmap(BackgroundRemover.loadOrientedBitmap(imagePath, 1600));
            } catch (Exception error) {
                System.out.println("[AnalysisResultActivity] Preview decode failed: " + error.getMessage());
            }
        }

        setupStaticForm();
        loadWardrobes();
        if (getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false)) {
            setupEditMode();
        } else {
            if (ApiClient.ENABLE_AI_ANALYSIS) {
                saveLocalPendingItem();
            }
            registerClothing();
        }
    }

    private void setupStaticForm() {
        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categoryLabels);
        categorySpinner.setAdapter(adapter);
        int hintIndex = Arrays.asList(CATEGORIES).indexOf(categoryHint);
        categorySpinner.setSelection(Math.max(hintIndex, 0));

        Spinner colorSpinner = findViewById(R.id.colorSpinner);
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, colorLabels);
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

        findViewById(R.id.editResultButton).setOnClickListener(v -> setDetailsVisible(!detailsVisible));
        findViewById(R.id.saveResultButton).setOnClickListener(v -> saveConfirmedResult());
        findViewById(R.id.deleteResultButton).setOnClickListener(v -> confirmDeleteClothing());
        Spinner wardrobeSpinner = findViewById(R.id.wardrobeSpinner);
        ArrayAdapter<String> wardrobeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.wardrobe_loading)});
        wardrobeSpinner.setAdapter(wardrobeAdapter);
        wardrobeSpinner.setEnabled(false);
        wardrobeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (wardrobes.isEmpty() || position < 0 || position >= wardrobes.size()) {
                    return;
                }
                selectedWardrobeId = wardrobes.get(position).id;
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void loadWardrobes() {
        if (!clothesBackendService.isConfigured()) {
            wardrobesReady = true;
            Spinner spinner = findViewById(R.id.wardrobeSpinner);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.save_on_device)}));
            spinner.setEnabled(false);
            return;
        }
        new Thread(() -> {
            try {
                List<Wardrobe> fetched = clothesBackendService.fetchWardrobeModels();
                runOnUiThread(() -> {
                    wardrobes.clear();
                    wardrobes.addAll(fetched);
                    List<String> labels = new ArrayList<>();
                    int selectedIndex = 0;
                    for (int i = 0; i < wardrobes.size(); i++) {
                        Wardrobe wardrobe = wardrobes.get(i);
                        labels.add(wardrobe.isDefault ? getString(R.string.default_suffix, wardrobe.name) : wardrobe.name);
                        if (!selectedWardrobeId.trim().isEmpty() && selectedWardrobeId.equals(wardrobe.id)) {
                            selectedIndex = i;
                        } else if (selectedWardrobeId.trim().isEmpty() && wardrobe.isDefault) {
                            selectedIndex = i;
                            selectedWardrobeId = wardrobe.id;
                        }
                    }
                    Spinner spinner = findViewById(R.id.wardrobeSpinner);
                    if (labels.isEmpty()) {
                        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.no_available_wardrobe)}));
                        spinner.setEnabled(false);
                        wardrobesReady = false;
                        setLoading(false, getString(R.string.create_wardrobe_first));
                    } else {
                        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
                        spinner.setSelection(selectedIndex);
                        spinner.setEnabled(true);
                        wardrobesReady = true;
                        setLoading(false, "");
                    }
                });
            } catch (Exception error) {
                System.out.println("[AnalysisResultActivity] Backend wardrobes fetch failed: " + error.getMessage());
                runOnUiThread(() -> {
                    wardrobesReady = false;
                    setLoading(false, getString(R.string.wardrobe_load_failed_retry));
                });
            }
        }).start();
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
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(
                displayNameOrDefault(getIntent().getStringExtra(EXTRA_NAME), categoryHint)
        );
        String note = getIntent().getStringExtra(EXTRA_NOTE);
        if (note != null) {
            if (note.contains("subcategory=")) {
                ((EditText) findViewById(R.id.subcategoryEditText)).setText(extractNoteValue(note, "subcategory"));
            }
            if (note.contains("style_tags=")) {
                ((EditText) findViewById(R.id.styleTagsEditText)).setText(extractNoteValue(note, "style_tags"));
            }
        }
        setDetailsVisible(true);
        findViewById(R.id.deleteResultButton).setVisibility(View.VISIBLE);
        setLoading(false, "");
    }

    private void confirmDeleteClothing() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_item_title)
                .setMessage(R.string.delete_item_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (prompt, which) -> deleteClothing())
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(R.color.fit_danger)));
        dialog.show();
    }

    private void deleteClothing() {
        boolean hasRemoteId = serverClothingId != null && !serverClothingId.trim().isEmpty();
        if (!hasRemoteId) {
            ClothingStorage.moveToTrash(this, localItemId, serverClothingId);
            UiUtils.toast(this, getString(R.string.item_deleted));
            finish();
            return;
        }
        if (!clothesBackendService.isConfigured()) {
            UiUtils.toast(this, getString(R.string.delete_requires_server));
            return;
        }
        setLoading(true, getString(R.string.item_deleting));
        new Thread(() -> {
            try {
                clothesBackendService.deleteClothing(serverClothingId);
                ClothingStorage.moveToTrash(this, localItemId, serverClothingId);
                runOnUiThread(() -> {
                    UiUtils.toast(this, getString(R.string.item_deleted));
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setLoading(false, getString(R.string.delete_retry));
                    UiUtils.toast(this, getString(R.string.item_delete_failed, error.getMessage()));
                });
            }
        }).start();
    }

    private void deleteLocalImage(String path) {
        if (path == null || path.trim().isEmpty()) return;
        try {
            File file = new File(path);
            String appFilesPath = getFilesDir().getCanonicalPath() + File.separator;
            if (file.getCanonicalPath().startsWith(appFilesPath) && file.exists() && !file.delete()) {
                System.out.println("[AnalysisResultActivity] Local image cleanup failed: " + path);
            }
        } catch (Exception error) {
            System.out.println("[AnalysisResultActivity] Local image cleanup failed: " + error.getMessage());
        }
    }

    private void registerClothing() {
        if (!ApiClient.ENABLE_AI_ANALYSIS) {
            result = AnalysisResult.fallback(categoryHint);
            bindResult(result);
            findViewById(R.id.resultConfidenceText).setVisibility(View.GONE);
            setLoading(false, "");
            return;
        }
        setLoading(true, getString(R.string.backend_saving_first));

        new Thread(() -> {
            try {
                if (imagePath == null || imagePath.trim().isEmpty()) {
                    throw new IllegalStateException(getString(R.string.image_missing));
                }
                ensurePendingClothingRow();

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
                    setLoading(false, getString(R.string.analysis_failed_saved));
                });
            }
        }).start();
    }

    private void bindResult(AnalysisResult result) {
        int categoryIndex = Arrays.asList(CATEGORIES).indexOf(result.mainCategory);
        ((TextView) findViewById(R.id.resultCategoryText)).setText(
                categoryIndex >= 0 ? categoryLabels[categoryIndex] : result.mainCategory
        );
        ((TextView) findViewById(R.id.resultConfidenceText)).setText(
                getString(R.string.analysis_confidence, Math.round(result.confidence * 100))
        );
        String color = normalizeColor(result.mainColor);
        int colorIndex = Arrays.asList(COLORS).indexOf(color);
        String colorLabel = colorIndex >= 0 ? colorLabels[colorIndex] : color;
        ((TextView) findViewById(R.id.colorSummaryText)).setText(
                getString(R.string.analysis_color_summary, colorLabel)
        );
        ((EditText) findViewById(R.id.clothingNameEditText)).setText(defaultNameFor(result.mainCategory));
        ((EditText) findViewById(R.id.subcategoryEditText)).setText(result.subcategory);
        ((EditText) findViewById(R.id.colorEditText)).setText(color);
        ((EditText) findViewById(R.id.styleTagsEditText)).setText(joinTags(result.styleTags));
        Spinner colorSpinner = findViewById(R.id.colorSpinner);
        if (colorIndex >= 0) {
            colorSpinner.setSelection(colorIndex);
        }

        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        categorySpinner.setSelection(Math.max(categoryIndex, 0));

        bindSeason(result.season);
    }

    private void resetFields() {
        if (result != null) {
            bindResult(result);
        }
    }

    private void setDetailsVisible(boolean visible) {
        detailsVisible = visible;
        findViewById(R.id.detailsContainer).setVisibility(visible ? View.VISIBLE : View.GONE);
        ((android.widget.Button) findViewById(R.id.editResultButton)).setText(
                visible ? getString(R.string.analysis_hide_details) : getString(R.string.ui_025)
        );
    }

    private void saveConfirmedResult() {
        if (clothesBackendService.isConfigured() && selectedWardrobeId.trim().isEmpty()) {
            UiUtils.toast(this, getString(R.string.select_wardrobe));
            return;
        }
        int categoryPosition = ((Spinner) findViewById(R.id.categorySpinner)).getSelectedItemPosition();
        String category = CATEGORIES[Math.max(0, Math.min(categoryPosition, CATEGORIES.length - 1))];
        String enteredName = ((EditText) findViewById(R.id.clothingNameEditText)).getText().toString().trim();
        String name = enteredName.isEmpty() || isGeneratedDefaultName(enteredName)
                ? defaultNameFor(category)
                : enteredName;
        String subcategory = ((EditText) findViewById(R.id.subcategoryEditText)).getText().toString().trim();
        String color = valueOrDefault(((EditText) findViewById(R.id.colorEditText)).getText().toString(), "gray");
        String styleTags = ((EditText) findViewById(R.id.styleTagsEditText)).getText().toString().trim();
        String season = selectedSeason();

        saveLocally(name, category, subcategory, color, season, styleTags, false);

        if (clothesBackendService.isConfigured()) {
            setLoading(true, backgroundRemoved
                    ? getString(R.string.saving_original_and_cutout)
                    : getString(R.string.saving_original));
            new Thread(() -> {
                try {
                    if (serverClothingId == null || serverClothingId.trim().isEmpty()) {
                        org.json.JSONObject row = clothesBackendService.createCompleted(
                                name,
                                category,
                                subcategory,
                                color,
                                season,
                                styleTags,
                                originalImagePath,
                                backgroundRemoved ? imagePath : null,
                                result,
                                selectedWardrobeId
                        );
                        Object id = row.opt("id");
                        serverClothingId = id == null ? null : String.valueOf(id);
                    } else {
                        clothesBackendService.updateCompleted(serverClothingId, name, category, subcategory, color, season, styleTags, imagePath, result, selectedWardrobeId);
                    }
                    ClothingStorage.updateSync(this, localItemId, serverClothingId, "synced", "");
                    runOnUiThread(() -> {
                        UiUtils.toast(this, getString(R.string.server_item_saved));
                        finish();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        System.out.println("[AnalysisResultActivity] Backend save failed, local item retained: " + error.getMessage());
                        ClothingStorage.updateSync(this, localItemId, serverClothingId, "sync_failed", error.getMessage());
                        UiUtils.toast(this, getString(R.string.server_save_local_fallback));
                        finish();
                    });
                }
            }).start();
            return;
        }

        UiUtils.toast(this, getString(R.string.saved_on_device));
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
        boolean manualEntry = !ApiClient.ENABLE_AI_ANALYSIS;
        ClothingStorage.saveOrReplace(this, new ClosetItem(
                localItemId,
                serverClothingId,
                selectedWardrobeId,
                name,
                category,
                color,
                season,
                note,
                imagePath,
                manualEntry ? "completed" : result == null ? "pending" : result.confidence > 0 ? "completed" : "failed",
                result == null ? "" : "",
                !manualEntry && (result == null || result.confidence <= 0),
                syncStatus,
                syncError,
                System.currentTimeMillis()
        ));
        if (finishAfterSave) {
            UiUtils.toast(this, getString(R.string.confirmed_info_saved));
            finish();
        }
    }

    private void saveLocalPendingItem() {
        ClosetItem item = new ClosetItem(
                java.util.UUID.randomUUID().toString(),
                "",
                selectedWardrobeId,
                defaultNameFor(categoryHint),
                categoryHint,
                dominantColorName(),
                getString(R.string.analysis_pending),
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
            org.json.JSONObject row = clothesBackendService.createPending(
                    categoryHint,
                    originalImagePath,
                    backgroundRemoved ? imagePath : null,
                    selectedWardrobeId
            );
            Object id = row.opt("id");
            serverClothingId = id == null ? null : String.valueOf(id);
            selectedWardrobeId = row.optString("wardrobe_id", selectedWardrobeId);
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
                selectedWardrobeId,
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
                selectedWardrobeId,
                defaultNameFor(categoryHint),
                categoryHint,
                "#CCCCCC",
                getString(R.string.analysis_failed),
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

        String normalized = season == null ? "" : season.toLowerCase(Locale.ROOT);
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
        return color.trim().toLowerCase(Locale.ROOT);
    }

    private String dominantColorName() {
        try {
            Bitmap bitmap = BackgroundRemover.loadOrientedBitmap(imagePath, 128);
            if (bitmap == null) {
                return "gray";
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true);
            long redTotal = 0;
            long greenTotal = 0;
            long blueTotal = 0;
            long alphaTotal = 0;
            for (int y = 0; y < scaled.getHeight(); y++) {
                for (int x = 0; x < scaled.getWidth(); x++) {
                    int pixel = scaled.getPixel(x, y);
                    int alpha = android.graphics.Color.alpha(pixel);
                    if (alpha < 32) continue;
                    redTotal += (long) android.graphics.Color.red(pixel) * alpha;
                    greenTotal += (long) android.graphics.Color.green(pixel) * alpha;
                    blueTotal += (long) android.graphics.Color.blue(pixel) * alpha;
                    alphaTotal += alpha;
                }
            }
            if (scaled != bitmap) scaled.recycle();
            bitmap.recycle();
            if (alphaTotal == 0) return "gray";
            int red = (int) (redTotal / alphaTotal);
            int green = (int) (greenTotal / alphaTotal);
            int blue = (int) (blueTotal / alphaTotal);
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
                    null,
                    analyzed.mainCategory,
                    analyzed.subcategory,
                    analyzed.mainColor,
                    analyzed.season,
                    joinTags(analyzed.styleTags),
                    imagePath,
                    analyzed,
                    selectedWardrobeId
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
        findViewById(R.id.saveResultButton).setEnabled(!loading && wardrobesReady);
        findViewById(R.id.editResultButton).setEnabled(!loading);
        findViewById(R.id.deleteResultButton).setEnabled(!loading);
    }

    private String defaultNameFor(String category) {
        switch (category) {
            case "top":
                return getString(R.string.new_top);
            case "bottom":
                return getString(R.string.new_bottom);
            case "outer":
                return getString(R.string.new_outer);
            case "shoes":
                return getString(R.string.new_shoes);
            case "hat":
                return getString(R.string.new_hat);
            case "bag":
                return getString(R.string.new_bag);
            case "dress":
                return getString(R.string.new_dress);
            default:
                return getString(R.string.new_clothing);
        }
    }

    private boolean isGeneratedDefaultName(String name) {
        for (String category : CATEGORIES) {
            if (defaultNameFor(category).equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String displayNameOrDefault(String name, String category) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || Arrays.asList(CATEGORIES).contains(trimmed)) {
            return defaultNameFor(category);
        }
        return trimmed;
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
