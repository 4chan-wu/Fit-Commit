package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;

/**
 * AI 서버 /predict 호출 책임을 분리한 서비스입니다.
 * 실제 이미지 업로드는 Multipart 연결 시 이 클래스에 구현합니다.
 */
public class FashionAiService {
    public AnalysisResult analyzeSample() {
        // TODO: AI 서버 POST /predict multipart 연동
        return AnalysisResult.sample();
    }
}
