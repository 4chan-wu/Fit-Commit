package com.fitcommit.app.models;

public class Wardrobe {
    public final String id;
    public final String userId;
    public final String name;
    public final String description;
    public final boolean isDefault;

    public Wardrobe(String id, String userId, String name, String description, boolean isDefault) {
        this.id = id == null ? "" : id;
        this.userId = userId == null ? "" : userId;
        this.name = name == null || name.trim().isEmpty() ? "기본 옷장" : name;
        this.description = description == null ? "" : description;
        this.isDefault = isDefault;
    }

    @Override
    public String toString() {
        return isDefault ? name + " · 기본" : name;
    }
}
