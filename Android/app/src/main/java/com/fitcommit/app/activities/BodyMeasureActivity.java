package com.fitcommit.app.activities;

public class BodyMeasureActivity extends BaseCameraActivity {
    @Override
    protected String getScreenTitle() {
        return "신체 비율 측정";
    }

    @Override
    protected String getGuideText() {
        return "머리부터 발끝까지 전신이 화면에 들어오도록 2~3m 정도 떨어져 서 주세요.\n현재 단계에서는 카메라 프리뷰만 제공하며, 다음 단계에서 MediaPipe Pose 분석을 연결합니다.";
    }

    @Override
    protected String getAnalysisTitle() {
        return "분석 대기 중";
    }

    @Override
    protected String getAnalysisBody() {
        return "2단계에서 어깨, 골반, 무릎, 발목 landmark를 추출해 상체:하체, 어깨:골반 등 상대 비율을 표시합니다.";
    }
}
