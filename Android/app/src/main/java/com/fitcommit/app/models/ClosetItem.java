package com.fitcommit.app.models;

/**
 * iOS ClosetItem 모델에 대응하는 Android용 의류 데이터 모델입니다.
 * 로컬 저장, 서버 동기화, AI 분석 상태를 함께 들고 갑니다.
 */
public class ClosetItem {
    public final String localId;
    public final String remoteId;
    public final String wardrobeId;
    public final String name;
    public final String category;
    public final String color;
    public final String season;
    public final String note;
    public final String imagePath;
    public final String analysisStatus;
    public final String analysisError;
    public final boolean needsAnalysis;
    public final String syncStatus;
    public final String syncError;
    public final long createdAt;

    public ClosetItem(String name, String category, String color, String season) {
        this(name, category, color, season, "", "");
    }

    public ClosetItem(String name, String category, String color, String season, String note, String imagePath) {
        this(name, category, color, season, note, imagePath, "completed", false);
    }

    public ClosetItem(
            String name,
            String category,
            String color,
            String season,
            String note,
            String imagePath,
            String analysisStatus,
            boolean needsAnalysis
    ) {
        this(
                java.util.UUID.randomUUID().toString(),
                "",
                "",
                name,
                category,
                color,
                season,
                note,
                imagePath,
                analysisStatus,
                "",
                needsAnalysis,
                "local_only",
                "",
                System.currentTimeMillis()
        );
    }

    public ClosetItem(
            String localId,
            String remoteId,
            String name,
            String category,
            String color,
            String season,
            String note,
            String imagePath,
            String analysisStatus,
            String analysisError,
            boolean needsAnalysis,
            String syncStatus,
            String syncError,
            long createdAt
    ) {
        this(
                localId,
                remoteId,
                "",
                name,
                category,
                color,
                season,
                note,
                imagePath,
                analysisStatus,
                analysisError,
                needsAnalysis,
                syncStatus,
                syncError,
                createdAt
        );
    }

    public ClosetItem(
            String localId,
            String remoteId,
            String wardrobeId,
            String name,
            String category,
            String color,
            String season,
            String note,
            String imagePath,
            String analysisStatus,
            String analysisError,
            boolean needsAnalysis,
            String syncStatus,
            String syncError,
            long createdAt
    ) {
        this.localId = localId == null || localId.trim().isEmpty() ? java.util.UUID.randomUUID().toString() : localId;
        this.remoteId = remoteId == null ? "" : remoteId;
        this.wardrobeId = wardrobeId == null ? "" : wardrobeId;
        this.name = name;
        this.category = category;
        this.color = color;
        this.season = season;
        this.note = note;
        this.imagePath = imagePath;
        this.analysisStatus = analysisStatus == null || analysisStatus.trim().isEmpty() ? "completed" : analysisStatus;
        this.analysisError = analysisError == null ? "" : analysisError;
        this.needsAnalysis = needsAnalysis;
        this.syncStatus = syncStatus == null || syncStatus.trim().isEmpty() ? "local_only" : syncStatus;
        this.syncError = syncError == null ? "" : syncError;
        this.createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
    }
}
