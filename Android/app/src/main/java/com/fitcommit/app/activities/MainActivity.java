package com.fitcommit.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.adapters.ClosetCardAdapter;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.utils.UiUtils;

import java.util.Arrays;
import java.util.List;

/**
 * iOS MainTabView에 대응하는 메인 화면입니다.
 * 하단 캡슐형 탭 바를 유지하고, 선택된 탭의 XML 화면을 contentFrame에 교체합니다.
 */
public class MainActivity extends Activity {
    private FrameLayout contentFrame;
    private TextView tabHome;
    private TextView tabCloset;
    private TextView tabAi;
    private TextView tabProfile;

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

    private void setSelectedTab(TextView selected) {
        List<TextView> tabs = Arrays.asList(tabHome, tabCloset, tabAi, tabProfile);
        for (TextView tab : tabs) {
            tab.setBackgroundResource(tab == selected ? R.drawable.bg_tab_selected : 0);
            tab.setTextColor(getColor(tab == selected ? R.color.fit_text : R.color.fit_text_secondary));
        }
    }

    private View inflate(int layoutId) {
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(layoutId, contentFrame, false);
        contentFrame.addView(view);
        return view;
    }

    private void showHome() {
        setSelectedTab(tabHome);
        inflate(R.layout.screen_home);
    }

    /**
     * iOS ClosetView의 AR 기능 허브와 의류 그리드를 더미 데이터로 구성합니다.
     */
    private void showCloset() {
        setSelectedTab(tabCloset);
        View view = inflate(R.layout.screen_closet);

        Button addButton = view.findViewById(R.id.addClothingButton);
        addButton.setOnClickListener(v -> startActivity(new Intent(this, AnalysisResultActivity.class)));

        view.findViewById(R.id.spaceScanButton).setOnClickListener(v -> UiUtils.toast(this, "3D 공간 스캔은 추후 ARCore로 연결됩니다."));
        view.findViewById(R.id.bodyMeasureButton).setOnClickListener(v -> UiUtils.toast(this, "신체 비율 측정은 추후 카메라 모듈과 연결됩니다."));

        GridLayout grid = view.findViewById(R.id.closetGrid);
        new ClosetCardAdapter(this).bind(grid, sampleClosetItems());
    }

    private void showAiLanding() {
        setSelectedTab(tabAi);
        View view = inflate(R.layout.screen_ai_landing);
        view.findViewById(R.id.startChatButton).setOnClickListener(v -> startActivity(new Intent(this, AIChatActivity.class)));
        view.findViewById(R.id.chatHistoryButton).setOnClickListener(v -> UiUtils.toast(this, "채팅 히스토리는 Backend 연동 후 표시됩니다."));
    }

    private void showProfile() {
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
