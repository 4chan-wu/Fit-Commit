package com.fitcommit.app.models;

/**
 * CLIP/FashionCLIP 기반 /predict 응답의 analysis 필드와 동일한 형태의 화면 모델입니다.
 */
public class AnalysisResult {
    public final String mainCategory;
    public final String subcategory;
    public final String mainColor;
    public final String season;
    public final String[] styleTags;
    public final double confidence;
    public final String method;
    public final String imageUrl;

    public AnalysisResult(
            String mainCategory,
            String subcategory,
            String mainColor,
            String season,
            String[] styleTags,
            double confidence,
            String method,
            String imageUrl
    ) {
        this.mainCategory = mainCategory;
        this.subcategory = subcategory;
        this.mainColor = mainColor;
        this.season = season;
        this.styleTags = styleTags;
        this.confidence = confidence;
        this.method = method;
        this.imageUrl = imageUrl;
    }

    public static AnalysisResult fallback(String categoryHint) {
        return new AnalysisResult(categoryHint, "", "#CCCCCC", "All-Season", new String[0], 0, "fallback", "");
    }
}
