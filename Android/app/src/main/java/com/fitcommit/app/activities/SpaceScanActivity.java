package com.fitcommit.app.activities;

public class SpaceScanActivity extends BaseCameraActivity {
    @Override
    protected String getScreenTitle() {
        return "공간 스캔";
    }

    @Override
    protected String getGuideText() {
        return "방 전체가 보이도록 천천히 카메라를 움직여 주세요.\n현재 단계에서는 카메라 프리뷰만 제공하며, 3D 공간 분석은 ARCore 단계에서 연결합니다.";
    }

    @Override
    protected String getAnalysisTitle() {
        return "1단계 MVP";
    }

    @Override
    protected String getAnalysisBody() {
        return "카메라 화면이 정상적으로 열리면 공간 스캔 MVP가 동작 중입니다.";
    }
}
