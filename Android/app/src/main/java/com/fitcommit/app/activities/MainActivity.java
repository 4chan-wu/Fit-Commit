package com.fitcommit.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fitcommit.app.R;
import com.fitcommit.app.adapters.ClosetCardAdapter;
import com.fitcommit.app.models.BodyMeasurement;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.models.Wardrobe;
import com.fitcommit.app.services.BodyMeasurementStorage;
import com.fitcommit.app.services.ClothesBackendService;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.services.TokenManager;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * iOS MainTabView에 대응하는 메인 화면입니다.
 * 고정형 하단 탭을 유지하고, 선택된 탭의 XML 화면을 contentFrame에 교체합니다.
 */
public class MainActivity extends Activity {
    private static final int TAB_ANIMATION_DURATION_MS = 180;
    private final int[] tabLabelResIds = {
            R.string.ui_075,
            R.string.ui_076,
            R.string.ui_077,
            R.string.ui_078
    };
    private final int[] tabIconResIds = {
            R.drawable.ic_tab_home,
            R.drawable.ic_tab_closet,
            R.drawable.ic_tab_sparkles,
            R.drawable.ic_tab_user
    };
    private FrameLayout contentFrame;
    private TextView tabHome;
    private TextView tabCloset;
    private TextView tabAi;
    private TextView tabProfile;
    private String currentScreen = "";
    private String selectedWardrobeId = "";
    private final List<Wardrobe> availableWardrobes = new ArrayList<>();
    private ClothesBackendService clothesBackendService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clothesBackendService = new ClothesBackendService(this);

        contentFrame = findViewById(R.id.contentFrame);
        applySystemBarInsets();
        tabHome = findViewById(R.id.tabHome);
        tabCloset = findViewById(R.id.tabCloset);
        tabAi = findViewById(R.id.tabAi);
        tabProfile = findViewById(R.id.tabProfile);

        tabHome.setOnClickListener(v -> showHome());
        tabCloset.setOnClickListener(v -> showCloset());
        tabAi.setOnClickListener(v -> showAiLanding());
        tabProfile.setOnClickListener(v -> showProfile());

        showHome();
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.mainRoot);
        View bottomWrapper = findViewById(R.id.bottomTabWrapper);
        int horizontalPadding = dpToPx(12);
        int minimumBottomPadding = dpToPx(8);
        int tabBarHeight = dpToPx(64);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomPadding = Math.max(minimumBottomPadding, systemBars.bottom);
            view.setPadding(0, systemBars.top, 0, 0);
            bottomWrapper.setPadding(horizontalPadding, 0, horizontalPadding, bottomPadding);
            ViewGroup.LayoutParams params = bottomWrapper.getLayoutParams();
            params.height = tabBarHeight + bottomPadding;
            bottomWrapper.setLayoutParams(params);
            contentFrame.setPadding(0, 0, 0, tabBarHeight + bottomPadding);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("closet".equals(currentScreen)) {
            showCloset();
        } else if ("profile".equals(currentScreen)) {
            showProfile();
        }
    }

    private void setSelectedTab(TextView selected) {
        List<TextView> tabs = Arrays.asList(tabHome, tabCloset, tabAi, tabProfile);
        for (int i = 0; i < tabs.size(); i++) {
            TextView tab = tabs.get(i);
            boolean isSelected = tab == selected;
            int tabColor = getColor(isSelected ? R.color.fit_primary : R.color.fit_text_secondary);
            tab.setBackgroundResource(isSelected ? R.drawable.bg_tab_selected : 0);
            tab.setTextColor(tabColor);
            tab.setText(tabLabelResIds[i]);
            Drawable icon = ContextCompat.getDrawable(this, tabIconResIds[i]);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(tabColor);
                int iconSize = dpToPx(21);
                icon.setBounds(0, 0, iconSize, iconSize);
            }
            tab.setCompoundDrawables(null, icon, null, null);
            tab.setContentDescription(getString(tabLabelResIds[i]));
            tab.setGravity(Gravity.CENTER);
            tab.setIncludeFontPadding(false);
            tab.setTextSize(11);
            tab.setAlpha(isSelected ? 1f : 0.78f);
            tab.animate()
                    .scaleX(isSelected ? 1f : 0.97f)
                    .scaleY(isSelected ? 1f : 0.97f)
                    .setDuration(TAB_ANIMATION_DURATION_MS)
                    .start();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private View inflate(int layoutId) {
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(layoutId, contentFrame, false);
        contentFrame.addView(view);
        return view;
    }

    private void showHome() {
        currentScreen = "home";
        setSelectedTab(tabHome);
        View view = inflate(R.layout.screen_home);
        view.findViewById(R.id.viewDailyOutfitButton).setOnClickListener(v -> showCloset());
    }

    /**
     * iOS ClosetView와 동일하게 AR 기능 허브와 로컬/서버 의류 목록을 구성합니다.
     */
    private void showCloset() {
        currentScreen = "closet";
        setSelectedTab(tabCloset);
        View view = inflate(R.layout.screen_closet);

        RecyclerView grid = view.findViewById(R.id.closetGrid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setHasFixedSize(false);
        ClosetCardAdapter adapter = new ClosetCardAdapter(this, new ClosetCardAdapter.OnItemActionListener() {
            @Override
            public void onOpen(ClosetItem item) {
                openClothingEditor(item);
            }

            @Override
            public void onManage(ClosetItem item) {
                showClothingActions(item);
            }
        });
        grid.setAdapter(adapter);

        TextView countText = view.findViewById(R.id.closetCountText);
        View emptyState = view.findViewById(R.id.closetEmptyState);
        TextView noResultsText = view.findViewById(R.id.closetNoResultsText);
        TextView addClothingButton = view.findViewById(R.id.addClothingButton);
        TextView sortButton = view.findViewById(R.id.closetSortButton);
        TextView backendSyncStatusText = view.findViewById(R.id.backendSyncStatusText);
        TextView backendRefreshButton = view.findViewById(R.id.backendRefreshButton);
        View backendStatusContainer = view.findViewById(R.id.backendStatusContainer);
        LinearLayout wardrobeRow = view.findViewById(R.id.wardrobeFilterRow);
        LinearLayout categoryRow = view.findViewById(R.id.categoryFilterRow);
        List<ClosetItem> sourceItems = new ArrayList<>();
        String[] selectedCategory = {"all"};
        String[] selectedSeason = {"all"};
        String[] searchQuery = {""};
        String[] sortMode = {"recent"};

        countText.setText(R.string.ui_096);
        backendStatusContainer.setVisibility(View.GONE);
        backendRefreshButton.setOnClickListener(v -> showCloset());
        bindWardrobeFilters(wardrobeRow, Collections.emptyList());

        Runnable refresh = () -> applyClosetFilters(
                sourceItems,
                adapter,
                countText,
                emptyState,
                noResultsText,
                addClothingButton,
                selectedCategory[0],
                selectedSeason[0],
                searchQuery[0],
                sortMode[0]
        );
        bindCategoryFilters(categoryRow, selectedCategory, refresh);

        View.OnClickListener addClothing = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            openAddClothing();
        };
        addClothingButton.setTag(true);
        addClothingButton.setOnClickListener(addClothing);
        view.findViewById(R.id.firstClothingButton).setOnClickListener(addClothing);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (Math.abs(dy) < dpToPx(2) || addClothingButton.getVisibility() != View.VISIBLE) {
                    return;
                }
                setClothingAddButtonExpanded(addClothingButton, dy < 0);
            }
        });
        view.findViewById(R.id.createWardrobeButton).setOnClickListener(v -> showCreateWardrobeDialog());
        view.findViewById(R.id.wardrobeManageButton).setOnClickListener(v -> showWardrobeManagerDialog());
        view.findViewById(R.id.bodyMeasureButton)
                .setOnClickListener(v -> startActivity(new Intent(this, BodyMeasureActivity.class)));
        view.findViewById(R.id.spaceScanButton)
                .setOnClickListener(v -> startActivity(new Intent(this, SpaceScanActivity.class)));
        view.findViewById(R.id.searchClosetButton).setOnClickListener(v -> showClosetSearchDialog(
                searchQuery[0],
                query -> {
                    searchQuery[0] = query;
                    refresh.run();
                }
        ));
        view.findViewById(R.id.filterClosetButton).setOnClickListener(v -> showSeasonFilterDialog(
                selectedSeason[0],
                season -> {
                    selectedSeason[0] = season;
                    refresh.run();
                }
        ));
        sortButton.setOnClickListener(v -> showClosetSortDialog(sortMode[0], mode -> {
            sortMode[0] = mode;
            sortButton.setText("name".equals(mode) ? R.string.sort_name_short : R.string.ui_107);
            refresh.run();
        }));

        if (clothesBackendService.isConfigured()) {
            new Thread(() -> {
                try {
                    List<ClosetItem> backendItems = clothesBackendService.fetchClothes(selectedWardrobeId);
                    List<Wardrobe> wardrobes = Collections.emptyList();
                    boolean wardrobeFetchSucceeded = true;
                    try {
                        wardrobes = clothesBackendService.fetchWardrobeModels();
                    } catch (Exception wardrobeError) {
                        wardrobeFetchSucceeded = false;
                        System.out.println("[MainActivity] Backend wardrobes fetch failed: " + wardrobeError.getMessage());
                    }
                    List<Wardrobe> fetchedWardrobes = wardrobes;
                    boolean fetchedWardrobesSuccessfully = wardrobeFetchSucceeded;
                    runOnUiThread(() -> {
                        if (!"closet".equals(currentScreen)) {
                            return;
                        }
                        if (selectedWardrobeId == null || selectedWardrobeId.trim().isEmpty()) {
                            ClothingStorage.reconcileActiveWithBackend(this, backendItems);
                        }
                        bindWardrobeFilters(wardrobeRow, fetchedWardrobes);
                        availableWardrobes.clear();
                        availableWardrobes.addAll(fetchedWardrobes);
                        sourceItems.clear();
                        sourceItems.addAll(mergeBackendAndLocalItems(backendItems));
                        if (!fetchedWardrobesSuccessfully) {
                            backendStatusContainer.setVisibility(View.VISIBLE);
                            backendSyncStatusText.setText(R.string.wardrobe_fetch_failed);
                            backendSyncStatusText.setTextColor(getColor(R.color.fit_accent));
                        } else {
                            backendStatusContainer.setVisibility(View.GONE);
                        }
                        refresh.run();
                    });
                } catch (Exception error) {
                    System.out.println("[MainActivity] Backend clothes fetch failed, using local fallback: " + error.getMessage());
                    runOnUiThread(() -> {
                        if (!"closet".equals(currentScreen)) {
                            return;
                        }
                        sourceItems.clear();
                        sourceItems.addAll(loadLocalClosetItems());
                        backendStatusContainer.setVisibility(View.VISIBLE);
                        backendSyncStatusText.setText(R.string.local_fallback_unstable);
                        backendSyncStatusText.setTextColor(getColor(R.color.fit_danger));
                        refresh.run();
                    });
                }
            }).start();
        } else {
            sourceItems.addAll(loadLocalClosetItems());
            backendStatusContainer.setVisibility(View.VISIBLE);
            backendSyncStatusText.setText(R.string.local_fallback);
            backendSyncStatusText.setTextColor(getColor(R.color.fit_danger));
            refresh.run();
        }
    }

    private List<ClosetItem> loadLocalClosetItems() {
        return new ArrayList<>(ClothingStorage.load(this));
    }

    private void openAddClothing() {
        Intent intent = new Intent(this, AddClothingActivity.class);
        intent.putExtra(AddClothingActivity.EXTRA_WARDROBE_ID, selectedWardrobeId);
        startActivity(intent);
    }

    private void openClothingEditor(ClosetItem item) {
        Intent intent = new Intent(this, AnalysisResultActivity.class);
        intent.putExtra(AnalysisResultActivity.EXTRA_EDIT_MODE, true);
        intent.putExtra(AnalysisResultActivity.EXTRA_LOCAL_ID, item.localId);
        intent.putExtra(AnalysisResultActivity.EXTRA_REMOTE_ID, item.remoteId);
        intent.putExtra(AnalysisResultActivity.EXTRA_WARDROBE_ID, item.wardrobeId);
        intent.putExtra(AnalysisResultActivity.EXTRA_IMAGE_PATH, item.imagePath);
        intent.putExtra(AnalysisResultActivity.EXTRA_CATEGORY_HINT, item.category);
        intent.putExtra(AnalysisResultActivity.EXTRA_NAME, item.name);
        intent.putExtra(AnalysisResultActivity.EXTRA_COLOR, item.color);
        intent.putExtra(AnalysisResultActivity.EXTRA_SEASON, item.season);
        intent.putExtra(AnalysisResultActivity.EXTRA_NOTE, item.note);
        startActivity(intent);
    }

    private void showClothingActions(ClosetItem item) {
        String[] actions = {getString(R.string.item_edit), getString(R.string.item_move), getString(R.string.item_delete)};
        new AlertDialog.Builder(this)
                .setTitle(safe(item.name).isEmpty() ? getString(R.string.item_manage_title) : item.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        openClothingEditor(item);
                    } else if (which == 1) {
                        showMoveClothingDialog(item);
                    } else {
                        confirmDeleteClothing(item);
                    }
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void showMoveClothingDialog(ClosetItem item) {
        if (availableWardrobes.isEmpty()) {
            UiUtils.toast(this, getString(R.string.wardrobe_load_first));
            return;
        }
        String[] labels = new String[availableWardrobes.size()];
        int checked = 0;
        for (int i = 0; i < availableWardrobes.size(); i++) {
            Wardrobe wardrobe = availableWardrobes.get(i);
            labels[i] = wardrobe.isDefault ? getString(R.string.default_suffix, wardrobe.name) : wardrobe.name;
            if (wardrobe.id.equals(item.wardrobeId)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_destination)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    moveClothing(item, availableWardrobes.get(which));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void moveClothing(ClosetItem item, Wardrobe wardrobe) {
        if (wardrobe.id.equals(item.wardrobeId)) {
            UiUtils.toast(this, getString(R.string.already_in_wardrobe));
            return;
        }
        boolean hasRemoteId = item.remoteId != null && !item.remoteId.trim().isEmpty();
        if (!hasRemoteId) {
            ClothingStorage.updateWardrobe(this, item, wardrobe.id);
            UiUtils.toast(this, getString(R.string.item_moved, wardrobe.name));
            showCloset();
            return;
        }
        if (!clothesBackendService.isConfigured()) {
            UiUtils.toast(this, getString(R.string.move_requires_server));
            return;
        }
        UiUtils.toast(this, getString(R.string.item_moving));
        new Thread(() -> {
            try {
                clothesBackendService.moveClothing(item.remoteId, wardrobe.id);
                ClothingStorage.updateWardrobe(this, item, wardrobe.id);
                runOnUiThread(() -> {
                    UiUtils.toast(this, getString(R.string.item_moved, wardrobe.name));
                    showCloset();
                });
            } catch (Exception error) {
                runOnUiThread(() -> UiUtils.toast(this, getString(R.string.item_move_failed, error.getMessage())));
            }
        }).start();
    }

    private void confirmDeleteClothing(ClosetItem item) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_item_title)
                .setMessage(getString(R.string.delete_named_item_message, safe(item.name)))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (prompt, which) -> deleteClothing(item))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(R.color.fit_danger)));
        dialog.show();
    }

    private void deleteClothing(ClosetItem item) {
        boolean hasRemoteId = item.remoteId != null && !item.remoteId.trim().isEmpty();
        if (!hasRemoteId) {
            ClothingStorage.moveToTrash(this, item.localId, item.remoteId);
            UiUtils.toast(this, getString(R.string.item_deleted));
            showCloset();
            return;
        }
        if (!clothesBackendService.isConfigured()) {
            UiUtils.toast(this, getString(R.string.delete_requires_server));
            return;
        }

        UiUtils.toast(this, getString(R.string.item_deleting));
        new Thread(() -> {
            try {
                clothesBackendService.deleteClothing(item.remoteId);
                ClothingStorage.moveToTrash(this, item.localId, item.remoteId);
                runOnUiThread(() -> {
                    UiUtils.toast(this, getString(R.string.item_deleted));
                    showCloset();
                });
            } catch (Exception error) {
                runOnUiThread(() -> UiUtils.toast(this, getString(R.string.item_delete_failed, error.getMessage())));
            }
        }).start();
    }

    private void deleteLocalClothingImage(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;
        try {
            File file = new File(imagePath);
            String appFilesPath = getFilesDir().getCanonicalPath() + File.separator;
            if (file.getCanonicalPath().startsWith(appFilesPath) && file.exists() && !file.delete()) {
                System.out.println("[MainActivity] Local clothing image cleanup failed: " + imagePath);
            }
        } catch (Exception error) {
            System.out.println("[MainActivity] Local clothing image cleanup failed: " + error.getMessage());
        }
    }

    private void applyClosetFilters(
            List<ClosetItem> sourceItems,
            ClosetCardAdapter adapter,
            TextView countText,
            View emptyState,
            TextView noResultsText,
            TextView addClothingButton,
            String category,
            String season,
            String query,
            String sortMode
    ) {
        List<ClosetItem> visibleItems = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (ClosetItem item : sourceItems) {
            if (!matchesClosetCategory(item, category) || !matchesClosetSeason(item, season)) {
                continue;
            }
            String searchable = (safe(item.name) + " " + safe(item.category) + " "
                    + safe(item.color) + " " + safe(item.season)).toLowerCase(Locale.ROOT);
            if (normalizedQuery.isEmpty() || searchable.contains(normalizedQuery)) {
                visibleItems.add(item);
            }
        }

        if ("name".equals(sortMode)) {
            visibleItems.sort(Comparator.comparing(item -> safe(item.name), String.CASE_INSENSITIVE_ORDER));
        } else {
            visibleItems.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
        }

        adapter.submitList(visibleItems);
        countText.setText(visibleItems.size() == sourceItems.size()
                ? getResources().getQuantityString(
                        R.plurals.closet_count_total,
                        visibleItems.size(),
                        visibleItems.size())
                : getResources().getQuantityString(
                        R.plurals.closet_count_filtered,
                        visibleItems.size(),
                        visibleItems.size(),
                        sourceItems.size()));
        boolean closetIsEmpty = sourceItems.isEmpty();
        boolean filtersHaveNoResults = !closetIsEmpty && visibleItems.isEmpty();
        emptyState.setVisibility(closetIsEmpty ? View.VISIBLE : View.GONE);
        noResultsText.setVisibility(filtersHaveNoResults ? View.VISIBLE : View.GONE);
        addClothingButton.setVisibility(closetIsEmpty ? View.GONE : View.VISIBLE);
    }

    private void setClothingAddButtonExpanded(TextView button, boolean expanded) {
        Object currentState = button.getTag();
        if (currentState instanceof Boolean && (Boolean) currentState == expanded) {
            return;
        }
        button.setTag(expanded);

        ViewGroup parent = (ViewGroup) button.getParent();
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(180);
        TransitionManager.beginDelayedTransition(parent, transition);

        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.width = dpToPx(expanded ? 140 : 52);
        button.setLayoutParams(params);
        button.setText(expanded ? getString(R.string.ui_112) : "");
        button.setCompoundDrawablePadding(expanded ? dpToPx(8) : 0);
        button.setPadding(expanded ? dpToPx(17) : 0, 0, expanded ? dpToPx(19) : 0, 0);
        button.setContentDescription(expanded ? getString(R.string.ui_112) : getString(R.string.add_item_button_description));
    }

    private boolean matchesClosetCategory(ClosetItem item, String selected) {
        if ("all".equals(selected)) return true;
        String category = safe(item.category).toLowerCase(Locale.ROOT);
        if ("top".equals(selected)) {
            return category.contains("top") || category.contains("shirt") || category.contains("hoodie")
                    || category.contains("상의") || category.contains("티셔츠") || category.contains("셔츠");
        }
        if ("bottom".equals(selected)) {
            return category.contains("bottom") || category.contains("pants") || category.contains("skirt")
                    || category.contains("하의") || category.contains("바지") || category.contains("스커트");
        }
        if ("outer".equals(selected)) {
            return category.contains("outer") || category.contains("jacket") || category.contains("coat")
                    || category.contains("아우터") || category.contains("재킷") || category.contains("코트");
        }
        if ("shoes".equals(selected)) {
            return category.contains("shoe") || category.contains("sneaker") || category.contains("신발");
        }
        if ("accessory".equals(selected)) {
            return category.contains("bag") || category.contains("hat") || category.contains("access")
                    || category.contains("가방") || category.contains("모자") || category.contains("소품");
        }
        return category.contains("dress") || category.contains("원피스");
    }

    private boolean matchesClosetSeason(ClosetItem item, String selected) {
        if ("all".equals(selected)) return true;
        String season = safe(item.season).toLowerCase(Locale.ROOT);
        if ("spring".equals(selected)) return season.contains("spring") || season.contains("봄") || season.contains("all");
        if ("summer".equals(selected)) return season.contains("summer") || season.contains("여름") || season.contains("all");
        if ("fall".equals(selected)) return season.contains("fall") || season.contains("autumn") || season.contains("가을") || season.contains("all");
        return season.contains("winter") || season.contains("겨울") || season.contains("all");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void bindCategoryFilters(LinearLayout row, String[] selectedCategory, Runnable refresh) {
        String[] labels = getResources().getStringArray(R.array.closet_category_filter_labels);
        String[] values = {"all", "top", "bottom", "outer", "shoes", "accessory", "dress"};
        row.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            String value = values[i];
            TextView chip = createClosetChip(labels[i], value.equals(selectedCategory[0]));
            chip.setOnClickListener(v -> {
                selectedCategory[0] = value;
                bindCategoryFilters(row, selectedCategory, refresh);
                refresh.run();
            });
            row.addView(chip);
        }
    }

    private TextView createClosetChip(String label, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13);
        chip.setTextColor(getColor(selected ? R.color.fit_on_dark : R.color.fit_text));
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(40)
        );
        params.setMargins(0, 0, dpToPx(8), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private void showClosetSearchDialog(String currentQuery, Consumer<String> onApply) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.search_hint);
        input.setText(currentQuery);
        input.setSelectAllOnFocus(true);
        int horizontalPadding = dpToPx(20);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_title)
                .setView(container)
                .setPositiveButton(R.string.action_search, (dialog, which) -> onApply.accept(input.getText().toString()))
                .setNeutralButton(R.string.action_reset, (dialog, which) -> onApply.accept(""))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showSeasonFilterDialog(String currentSeason, Consumer<String> onApply) {
        String[] labels = getResources().getStringArray(R.array.closet_season_filter_labels);
        String[] values = {"all", "spring", "summer", "fall", "winter"};
        int checked = Math.max(0, Arrays.asList(values).indexOf(currentSeason));
        new AlertDialog.Builder(this)
                .setTitle(R.string.season_filter_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onApply.accept(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showClosetSortDialog(String currentMode, Consumer<String> onApply) {
        String[] labels = getResources().getStringArray(R.array.closet_sort_labels);
        int checked = "name".equals(currentMode) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onApply.accept(which == 1 ? "name" : "recent");
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showClosetToolsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.wardrobe_tools_title)
                .setItems(new String[]{getString(R.string.create_wardrobe_action)}, (dialog, which) -> showCreateWardrobeDialog())
                .show();
    }

    private List<ClosetItem> mergeBackendAndLocalItems(List<ClosetItem> backendItems) {
        List<ClosetItem> merged = new ArrayList<>(backendItems);
        for (ClosetItem localItem : ClothingStorage.load(this)) {
            boolean matchesWardrobe = selectedWardrobeId == null
                    || selectedWardrobeId.trim().isEmpty()
                    || selectedWardrobeId.equals(localItem.wardrobeId);
            if (!matchesWardrobe) continue;
            int remoteIndex = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (localItem.remoteId != null && !localItem.remoteId.trim().isEmpty()
                        && localItem.remoteId.equals(merged.get(i).remoteId)) {
                    remoteIndex = i;
                    break;
                }
            }
            if (remoteIndex >= 0 && "sync_failed".equals(localItem.syncStatus)) {
                merged.set(remoteIndex, localItem);
            } else if (remoteIndex < 0
                    && (localItem.remoteId == null || localItem.remoteId.trim().isEmpty()
                    || "sync_failed".equals(localItem.syncStatus))) {
                merged.add(localItem);
            }
        }
        return merged;
    }

    private void bindWardrobeFilters(LinearLayout row, List<Wardrobe> wardrobes) {
        row.removeAllViews();
        addWardrobeFilterButton(row, getString(R.string.all_clothes), "", null);
        for (Wardrobe wardrobe : wardrobes) {
            addWardrobeFilterButton(row, wardrobe.name, wardrobe.id, wardrobe);
        }
    }

    private void addWardrobeFilterButton(LinearLayout row, String label, String wardrobeId, Wardrobe wardrobe) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        boolean isSelected = wardrobeId.equals(selectedWardrobeId == null ? "" : selectedWardrobeId);
        button.setTextColor(getColor(isSelected ? R.color.fit_on_dark : R.color.fit_text));
        button.setBackgroundResource(
                isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip
        );
        button.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        button.setTextSize(13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(40)
        );
        params.setMargins(0, 0, dpToPx(8), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            selectedWardrobeId = wardrobeId;
            showCloset();
        });
        if (wardrobe != null) {
            button.setOnLongClickListener(v -> {
                showWardrobeActionDialog(wardrobe);
                return true;
            });
        }
        row.addView(button);
    }

    private void showCreateWardrobeDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), 0, dpToPx(20), 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.wardrobe_name_hint);
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint(R.string.description_hint);
        container.addView(nameInput);
        container.addView(descriptionInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.create_wardrobe_title)
                .setView(container)
                .setPositiveButton(R.string.action_create, (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.createWardrobe(
                                nameInput.getText().toString().trim(),
                                descriptionInput.getText().toString().trim()
                        );
                        runOnUiThread(() -> {
                            UiUtils.toast(this, getString(R.string.wardrobe_created));
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, getString(R.string.wardrobe_create_failed, error.getMessage())));
                    }
                }).start())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showWardrobeActionDialog(Wardrobe wardrobe) {
        String[] actions = wardrobe.isDefault
                ? new String[]{getString(R.string.wardrobe_edit)}
                : new String[]{getString(R.string.wardrobe_edit), getString(R.string.action_delete)};
        new AlertDialog.Builder(this)
                .setTitle(wardrobe.name)
                .setItems(actions, (dialog, which) -> {
                    if (getString(R.string.action_delete).equals(actions[which])) {
                        deleteWardrobe(wardrobe);
                    } else {
                        showEditWardrobeDialog(wardrobe);
                    }
                })
                .show();
    }

    private void showWardrobeManagerDialog() {
        if (availableWardrobes.isEmpty()) {
            UiUtils.toast(this, getString(R.string.no_wardrobes_to_manage));
            return;
        }
        String[] labels = new String[availableWardrobes.size()];
        for (int i = 0; i < availableWardrobes.size(); i++) {
            Wardrobe wardrobe = availableWardrobes.get(i);
            labels[i] = wardrobe.isDefault ? getString(R.string.default_suffix, wardrobe.name) : wardrobe.name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_wardrobe_to_manage)
                .setItems(labels, (dialog, which) -> showWardrobeActionDialog(availableWardrobes.get(which)))
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void showEditWardrobeDialog(Wardrobe wardrobe) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), 0, dpToPx(20), 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.wardrobe_name_hint);
        nameInput.setText(wardrobe.name);
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint(R.string.description_hint);
        descriptionInput.setText(wardrobe.description);
        container.addView(nameInput);
        container.addView(descriptionInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_wardrobe_title)
                .setView(container)
                .setPositiveButton(R.string.action_save, (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.updateWardrobe(
                                wardrobe.id,
                                nameInput.getText().toString().trim(),
                                descriptionInput.getText().toString().trim()
                        );
                        runOnUiThread(() -> {
                            UiUtils.toast(this, getString(R.string.wardrobe_updated));
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, getString(R.string.wardrobe_update_failed, error.getMessage())));
                    }
                }).start())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteWardrobe(Wardrobe wardrobe) {
        if (wardrobe.isDefault) {
            UiUtils.toast(this, getString(R.string.default_wardrobe_cannot_delete));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_wardrobe_title)
                .setMessage(R.string.delete_wardrobe_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.deleteWardrobe(wardrobe.id);
                        if (wardrobe.id.equals(selectedWardrobeId)) {
                            selectedWardrobeId = "";
                        }
                        runOnUiThread(() -> {
                            UiUtils.toast(this, getString(R.string.wardrobe_deleted));
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, getString(R.string.wardrobe_delete_failed, error.getMessage())));
                    }
                }).start())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showAiLanding() {
        currentScreen = "ai";
        setSelectedTab(tabAi);
        View view = inflate(R.layout.screen_ai_landing);
        view.findViewById(R.id.startChatButton).setOnClickListener(v -> startActivity(new Intent(this, AIChatActivity.class)));
        view.findViewById(R.id.chatHistoryButton).setOnClickListener(v -> UiUtils.toast(this, getString(R.string.chat_history_later)));
    }

    private void showProfile() {
        currentScreen = "profile";
        setSelectedTab(tabProfile);
        View view = inflate(R.layout.screen_profile);
        BodyMeasurement latestMeasurement = BodyMeasurementStorage.latest(this);
        TextView measurementSummary = view.findViewById(R.id.bodyMeasurementSummaryText);
        if (latestMeasurement == null) {
            measurementSummary.setText(R.string.body_no_result);
        } else {
            measurementSummary.setText(getString(
                    R.string.body_summary,
                    latestMeasurement.bodyShape,
                    latestMeasurement.heightMeters * 100f,
                    latestMeasurement.shoulderWidthMeters * 100f,
                    latestMeasurement.hipWidthMeters * 100f
            ));
        }
        view.findViewById(R.id.startBodyMeasurementButton).setOnClickListener(v ->
                startActivity(new Intent(this, BodyMeasureActivity.class)));
        view.findViewById(R.id.privacyPolicyButton).setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)));
        view.findViewById(R.id.logoutButton).setOnClickListener(v -> {
            new TokenManager(this).clear();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        view.findViewById(R.id.deleteAccountButton).setOnClickListener(v -> confirmAccountDeletion());
    }

    private void confirmAccountDeletion() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.delete_account_action, (dialog, which) -> new com.fitcommit.app.services.AuthService(this)
                        .deleteAccount(new com.fitcommit.app.services.AuthService.AuthCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                runOnUiThread(() -> {
                                    ClothingStorage.clear(MainActivity.this);
                                    BodyMeasurementStorage.clearAll(MainActivity.this);
                                    UiUtils.toast(MainActivity.this, getString(R.string.account_deleted));
                                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                                    finish();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> UiUtils.toast(MainActivity.this, getString(R.string.account_delete_failed, message)));
                            }
                        }))
                .show();
    }

}
