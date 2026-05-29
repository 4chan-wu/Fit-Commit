package com.fitcommit.app.models;

/**
 * AI 서버 /predict 응답의 analysis 필드와 동일한 형태의 화면 모델입니다.
 */
public class AnalysisResult {
    public final String category;
    public final double categoryConfidence;
    public final String color;
    public final String season;
    public final double seasonConfidence;
    public final String seasonMethod;

    public AnalysisResult(
            String category,
            double categoryConfidence,
            String color,
            String season,
            double seasonConfidence,
            String seasonMethod
    ) {
        this.category = category;
        this.categoryConfidence = categoryConfidence;
        this.color = color;
        this.season = season;
        this.seasonConfidence = seasonConfidence;
        this.seasonMethod = seasonMethod;
    }

    public static AnalysisResult sample() {
        return new AnalysisResult("T-shirt", 0.92, "#1A237E", "Spring/Summer", 0.78, "mock");
    }
}
