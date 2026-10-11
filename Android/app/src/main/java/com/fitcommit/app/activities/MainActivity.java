package com.fitcommit.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fitcommit.app.R;
import com.fitcommit.app.adapters.ClosetCardAdapter;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.models.Wardrobe;
import com.fitcommit.app.services.ClothesBackendService;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.utils.UiUtils;

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
    private final String[] tabLabels = {"\uD648", "\uB0B4 \uC637\uC7A5", "AI \uCF54\uB514", "\uB9C8\uC774"};
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
    private final ClothesBackendService clothesBackendService = new ClothesBackendService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contentFrame = findViewById(R.id.contentFrame);
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

    @Override
    protected void onResume() {
        super.onResume();
        if ("closet".equals(currentScreen)) {
            showCloset();
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
            tab.setText(tabLabels[i]);
            Drawable icon = getDrawable(tabIconResIds[i]);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(tabColor);
                int iconSize = dpToPx(21);
                icon.setBounds(0, 0, iconSize, iconSize);
            }
            tab.setCompoundDrawables(null, icon, null, null);
            tab.setContentDescription(tabLabels[i]);
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
        view.findViewById(R.id.viewDailyOutfitButton).setOnClickListener(v -> showAiLanding());
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
        ClosetCardAdapter adapter = new ClosetCardAdapter(this);
        grid.setAdapter(adapter);

        TextView countText = view.findViewById(R.id.closetCountText);
        TextView emptyText = view.findViewById(R.id.closetEmptyText);
        TextView sortButton = view.findViewById(R.id.closetSortButton);
        LinearLayout wardrobeRow = view.findViewById(R.id.wardrobeFilterRow);
        LinearLayout categoryRow = view.findViewById(R.id.categoryFilterRow);
        List<ClosetItem> sourceItems = new ArrayList<>();
        String[] selectedCategory = {"all"};
        String[] selectedSeason = {"all"};
        String[] searchQuery = {""};
        String[] sortMode = {"recent"};

        countText.setText("불러오는 중");
        bindWardrobeFilters(wardrobeRow, Collections.emptyList());

        Runnable refresh = () -> applyClosetFilters(
                sourceItems,
                adapter,
                countText,
                emptyText,
                selectedCategory[0],
                selectedSeason[0],
                searchQuery[0],
                sortMode[0]
        );
        bindCategoryFilters(categoryRow, selectedCategory, refresh);

        view.findViewById(R.id.addClothingButton)
                .setOnClickListener(v -> startActivity(new Intent(this, AddClothingActivity.class)));
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
        view.findViewById(R.id.closetMoreButton).setOnClickListener(v -> showClosetToolsDialog());
        sortButton.setOnClickListener(v -> showClosetSortDialog(sortMode[0], mode -> {
            sortMode[0] = mode;
            sortButton.setText("name".equals(mode) ? "이름순 ⌄" : "최근 추가순 ⌄");
            refresh.run();
        }));

        if (clothesBackendService.isConfigured()) {
            new Thread(() -> {
                try {
                    List<Wardrobe> wardrobes = clothesBackendService.fetchWardrobeModels();
                    List<ClosetItem> backendItems = clothesBackendService.fetchClothes(selectedWardrobeId);
                    runOnUiThread(() -> {
                        bindWardrobeFilters(wardrobeRow, wardrobes);
                        sourceItems.clear();
                        sourceItems.addAll(mergeBackendAndLocalItems(backendItems));
                        refresh.run();
                    });
                } catch (Exception error) {
                    System.out.println("[MainActivity] Backend clothes fetch failed, using local fallback: " + error.getMessage());
                    runOnUiThread(() -> {
                        sourceItems.clear();
                        sourceItems.addAll(loadLocalClosetItems());
                        refresh.run();
                    });
                }
            }).start();
        } else {
            sourceItems.addAll(loadLocalClosetItems());
            refresh.run();
        }
    }

    private List<ClosetItem> loadLocalClosetItems() {
        List<ClosetItem> items = new ArrayList<>();
        items.addAll(ClothingStorage.load(this));
        if (items.isEmpty()) {
            items.addAll(sampleClosetItems());
        }
        return items;
    }

    private void applyClosetFilters(
            List<ClosetItem> sourceItems,
            ClosetCardAdapter adapter,
            TextView countText,
            TextView emptyText,
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
                ? visibleItems.size() + "개 아이템"
                : visibleItems.size() + " / " + sourceItems.size() + "개 아이템");
        emptyText.setVisibility(visibleItems.isEmpty() ? View.VISIBLE : View.GONE);
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
        String[] labels = {"전체", "상의", "하의", "아우터", "신발", "가방·소품", "원피스"};
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
        input.setHint("이름, 카테고리, 색상 검색");
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
                .setTitle("옷 검색")
                .setView(container)
                .setPositiveButton("검색", (dialog, which) -> onApply.accept(input.getText().toString()))
                .setNeutralButton("초기화", (dialog, which) -> onApply.accept(""))
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSeasonFilterDialog(String currentSeason, Consumer<String> onApply) {
        String[] labels = {"전체 계절", "봄", "여름", "가을", "겨울"};
        String[] values = {"all", "spring", "summer", "fall", "winter"};
        int checked = Math.max(0, Arrays.asList(values).indexOf(currentSeason));
        new AlertDialog.Builder(this)
                .setTitle("계절 필터")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onApply.accept(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showClosetSortDialog(String currentMode, Consumer<String> onApply) {
        String[] labels = {"최근 추가순", "이름순"};
        int checked = "name".equals(currentMode) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("정렬")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    onApply.accept(which == 1 ? "name" : "recent");
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showClosetToolsDialog() {
        String[] actions = {"3D 공간 스캔", "신체 비율 측정", "새 옷장 만들기"};
        new AlertDialog.Builder(this)
                .setTitle("옷장 도구")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, SpaceScanActivity.class));
                    } else if (which == 1) {
                        startActivity(new Intent(this, BodyMeasureActivity.class));
                    } else {
                        showCreateWardrobeDialog();
                    }
                })
                .show();
    }

    private List<ClosetItem> mergeBackendAndLocalItems(List<ClosetItem> backendItems) {
        List<ClosetItem> merged = new ArrayList<>(backendItems);
        java.util.HashSet<String> remoteIds = new java.util.HashSet<>();
        for (ClosetItem item : backendItems) {
            if (item.remoteId != null && !item.remoteId.trim().isEmpty()) {
                remoteIds.add(item.remoteId);
            }
        }
        for (ClosetItem localItem : ClothingStorage.load(this)) {
            boolean matchesWardrobe = selectedWardrobeId == null
                    || selectedWardrobeId.trim().isEmpty()
                    || selectedWardrobeId.equals(localItem.wardrobeId);
            if (matchesWardrobe && (localItem.remoteId == null || localItem.remoteId.trim().isEmpty() || !remoteIds.contains(localItem.remoteId))) {
                merged.add(localItem);
            }
        }
        return merged;
    }

    private void bindWardrobeFilters(LinearLayout row, List<Wardrobe> wardrobes) {
        row.removeAllViews();
        addWardrobeFilterButton(row, "전체", "", null);
        for (Wardrobe wardrobe : wardrobes) {
            addWardrobeFilterButton(row, wardrobe.toString(), wardrobe.id, wardrobe);
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
        nameInput.setHint("옷장 이름");
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("설명");
        container.addView(nameInput);
        container.addView(descriptionInput);

        new AlertDialog.Builder(this)
                .setTitle("옷장 만들기")
                .setView(container)
                .setPositiveButton("생성", (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.createWardrobe(
                                nameInput.getText().toString().trim(),
                                descriptionInput.getText().toString().trim()
                        );
                        runOnUiThread(() -> {
                            UiUtils.toast(this, "옷장이 생성되었습니다.");
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, "옷장 생성 실패: " + error.getMessage()));
                    }
                }).start())
                .setNegativeButton("취소", null)
                .show();
    }

    private void showWardrobeActionDialog(Wardrobe wardrobe) {
        String[] actions = wardrobe.isDefault ? new String[]{"수정"} : new String[]{"수정", "삭제"};
        new AlertDialog.Builder(this)
                .setTitle(wardrobe.name)
                .setItems(actions, (dialog, which) -> {
                    if ("삭제".equals(actions[which])) {
                        deleteWardrobe(wardrobe);
                    } else {
                        showEditWardrobeDialog(wardrobe);
                    }
                })
                .show();
    }

    private void showEditWardrobeDialog(Wardrobe wardrobe) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), 0, dpToPx(20), 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint("옷장 이름");
        nameInput.setText(wardrobe.name);
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("설명");
        descriptionInput.setText(wardrobe.description);
        container.addView(nameInput);
        container.addView(descriptionInput);

        new AlertDialog.Builder(this)
                .setTitle("옷장 수정")
                .setView(container)
                .setPositiveButton("저장", (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.updateWardrobe(
                                wardrobe.id,
                                nameInput.getText().toString().trim(),
                                descriptionInput.getText().toString().trim()
                        );
                        runOnUiThread(() -> {
                            UiUtils.toast(this, "옷장이 수정되었습니다.");
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, "옷장 수정 실패: " + error.getMessage()));
                    }
                }).start())
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteWardrobe(Wardrobe wardrobe) {
        if (wardrobe.isDefault) {
            UiUtils.toast(this, "기본 옷장은 삭제할 수 없습니다.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("옷장을 삭제할까요?")
                .setMessage("이 옷장에 있던 옷은 기본 옷장으로 이동됩니다.")
                .setPositiveButton("삭제", (dialog, which) -> new Thread(() -> {
                    try {
                        clothesBackendService.deleteWardrobe(wardrobe.id);
                        if (wardrobe.id.equals(selectedWardrobeId)) {
                            selectedWardrobeId = "";
                        }
                        runOnUiThread(() -> {
                            UiUtils.toast(this, "옷장이 삭제되었습니다.");
                            showCloset();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> UiUtils.toast(this, "옷장 삭제 실패: " + error.getMessage()));
                    }
                }).start())
                .setNegativeButton("취소", null)
                .show();
    }

    private void showAiLanding() {
        currentScreen = "ai";
        setSelectedTab(tabAi);
        View view = inflate(R.layout.screen_ai_landing);
        view.findViewById(R.id.startChatButton).setOnClickListener(v -> startActivity(new Intent(this, AIChatActivity.class)));
        view.findViewById(R.id.chatHistoryButton).setOnClickListener(v -> UiUtils.toast(this, "채팅 히스토리는 Backend 연동 후 표시됩니다."));
    }

    private void showProfile() {
        currentScreen = "profile";
        setSelectedTab(tabProfile);
        View view = inflate(R.layout.screen_profile);
        view.findViewById(R.id.scanHistoryButton).setOnClickListener(v -> UiUtils.toast(this, "스캔 히스토리는 로컬 저장소 연결 후 표시됩니다."));
        view.findViewById(R.id.profileChatHistoryButton).setOnClickListener(v -> showAiLanding());
        view.findViewById(R.id.logoutButton).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private List<ClosetItem> sampleClosetItems() {
        return Arrays.asList(
                new ClosetItem("네이비 셔츠", "Shirt", "#1A237E", "All-Season"),
                new ClosetItem("화이트 티셔츠", "T-shirt", "#F7F7F7", "Spring/Summer"),
                new ClosetItem("그레이 슬랙스", "Pants", "#6D7280", "All-Season"),
                new ClosetItem("블랙 후디", "Hoodie", "#20232A", "Fall/Winter")
        );
    }
}
