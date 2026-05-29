package com.fitcommit.app.models;

/**
 * iOS ClosetItem 모델에 대응하는 Android용 의류 데이터 모델입니다.
 * 실제 저장소가 연결되기 전까지는 화면 더미 데이터로 사용합니다.
 */
public class ClosetItem {
    public final String name;
    public final String category;
    public final String color;
    public final String season;
    public final String note;
    public final String imagePath;

    public ClosetItem(String name, String category, String color, String season) {
        this(name, category, color, season, "", "");
    }

    public ClosetItem(String name, String category, String color, String season, String note, String imagePath) {
        this.name = name;
        this.category = category;
        this.color = color;
        this.season = season;
        this.note = note;
        this.imagePath = imagePath;
    }
}
