package com.fitcommit.app.activities;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.adapters.ClosetCardAdapter;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.models.Wardrobe;
import com.fitcommit.app.services.ClothesBackendService;
import com.fitcommit.app.services.ClothingStorage;
import com.fitcommit.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * iOS MainTabView에 대응하는 메인 화면입니다.
 * 하단 캡슐형 탭 바를 유지하고, 선택된 탭의 XML 화면을 contentFrame에 교체합니다.
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
    private LinearLayout bottomTabBar;
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
        bottomTabBar = findViewById(R.id.bottomTabBar);
        tabHome = findViewById(R.id.tabHome);
        tabCloset = findViewById(R.id.tabCloset);
        tabAi = findViewById(R.id.tabAi);
        tabProfile = findViewById(R.id.tabProfile);

        LayoutTransition tabTransition = new LayoutTransition();
        tabTransition.setDuration(TAB_ANIMATION_DURATION_MS);
        bottomTabBar.setLayoutTransition(tabTransition);

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
            int tabColor = getColor(isSelected ? R.color.fit_text : R.color.fit_text_secondary);
            tab.setBackgroundResource(isSelected ? R.drawable.bg_tab_selected : 0);
            tab.setTextColor(tabColor);
            tab.setText(createTabText(tabIconResIds[i], isSelected ? tabLabels[i] : "", tabColor));
            tab.setCompoundDrawables(null, null, null, null);
            tab.setContentDescription(tabLabels[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setIncludeFontPadding(false);
            tab.setTextSize(12);
            tab.setAlpha(isSelected ? 1f : 0.72f);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tab.getLayoutParams();
            params.weight = isSelected ? 1.35f : 0.88f;
            tab.setLayoutParams(params);
            tab.animate()
                    .scaleX(isSelected ? 1f : 0.94f)
                    .scaleY(isSelected ? 1f : 0.94f)
                    .setDuration(TAB_ANIMATION_DURATION_MS)
                    .start();
        }
    }

    private SpannableString createTabText(int iconResId, String label, int tintColor) {
        String value = label.isEmpty() ? " " : "  " + label;
        SpannableString text = new SpannableString(value);
        Drawable icon = getDrawable(iconResId);
        if (icon == null) {
            return text;
        }
        icon = icon.mutate();
        icon.setTint(tintColor);
        int iconSize = dpToPx(16);
        icon.setBounds(0, 0, iconSize, iconSize);
        text.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
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
        inflate(R.layout.screen_home);
    }

    /**
     * iOS ClosetView와 동일하게 AR 기능 허브와 로컬/서버 의류 목록을 구성합니다.
     */
    private void showCloset() {
        currentScreen = "closet";
        setSelectedTab(tabCloset);
        View view = inflate(R.layout.screen_closet);

        Button addButton = view.findViewById(R.id.addClothingButton);
        addButton.setOnClickListener(v -> startActivity(new Intent(this, AddClothingActivity.class)));
        view.findViewById(R.id.manageWardrobesButton).setOnClickListener(v -> showCreateWardrobeDialog());

        view.findViewById(R.id.spaceScanButton).setOnClickListener(v -> startActivity(new Intent(this, SpaceScanActivity.class)));
        view.findViewById(R.id.bodyMeasureButton).setOnClickListener(v -> startActivity(new Intent(this, BodyMeasureActivity.class)));

        GridLayout grid = view.findViewById(R.id.closetGrid);
        TextView countText = view.findViewById(R.id.closetCountText);
        countText.setText("불러오는 중");

        if (clothesBackendService.isConfigured()) {
            new Thread(() -> {
                try {
                    List<Wardrobe> wardrobes = clothesBackendService.fetchWardrobeModels();
                    List<ClosetItem> backendItems = clothesBackendService.fetchClothes(selectedWardrobeId);
                    runOnUiThread(() -> {
                        bindWardrobeFilters(view.findViewById(R.id.wardrobeFilterRow), wardrobes);
                        List<ClosetItem> mergedItems = mergeBackendAndLocalItems(backendItems);
                        countText.setText(mergedItems.size() + "개");
                        new ClosetCardAdapter(this).bind(grid, mergedItems);
                    });
                } catch (Exception error) {
                    System.out.println("[MainActivity] Backend clothes fetch failed, using local fallback: " + error.getMessage());
                    runOnUiThread(() -> bindLocalCloset(grid, countText));
                }
            }).start();
        } else {
            bindLocalCloset(grid, countText);
        }
    }

    private void bindLocalCloset(GridLayout grid, TextView countText) {
        List<ClosetItem> items = new ArrayList<>();
        items.addAll(ClothingStorage.load(this));
        items.addAll(sampleClosetItems());
        countText.setText(items.size() + "개");
        new ClosetCardAdapter(this).bind(grid, items);
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
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(getColor(R.color.fit_text));
        button.setBackgroundResource(
                wardrobeId.equals(selectedWardrobeId == null ? "" : selectedWardrobeId)
                        ? R.drawable.bg_primary_button
                        : R.drawable.bg_soft_button
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
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
