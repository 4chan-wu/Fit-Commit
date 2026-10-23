package com.fitcommit.app.models;

public final class BodyMeasurement {
    public final String recordId;
    public final long measuredAt;
    public final float heightMeters;
    public final float shoulderWidthMeters;
    public final float hipWidthMeters;
    public final float armLengthMeters;
    public final float legLengthMeters;
    public final String bodyShape;
    public final String recommendation;
    public final String measurementMethod;
    public final float confidence;
    public final String algorithmVersion;

    public BodyMeasurement(
            long measuredAt,
            float heightMeters,
            float shoulderWidthMeters,
            float hipWidthMeters,
            float armLengthMeters,
            float legLengthMeters,
            String bodyShape,
            String recommendation
    ) {
        this(
                "", measuredAt, heightMeters, shoulderWidthMeters, hipWidthMeters,
                armLengthMeters, legLengthMeters, bodyShape, recommendation,
                "", 0f, "android-v1"
        );
    }

    public BodyMeasurement(
            String recordId,
            long measuredAt,
            float heightMeters,
            float shoulderWidthMeters,
            float hipWidthMeters,
            float armLengthMeters,
            float legLengthMeters,
            String bodyShape,
            String recommendation,
            String measurementMethod,
            float confidence,
            String algorithmVersion
    ) {
        this.recordId = recordId == null ? "" : recordId;
        this.measuredAt = measuredAt;
        this.heightMeters = heightMeters;
        this.shoulderWidthMeters = shoulderWidthMeters;
        this.hipWidthMeters = hipWidthMeters;
        this.armLengthMeters = armLengthMeters;
        this.legLengthMeters = legLengthMeters;
        this.bodyShape = bodyShape;
        this.recommendation = recommendation;
        this.measurementMethod = measurementMethod == null ? "" : measurementMethod;
        this.confidence = confidence;
        this.algorithmVersion = algorithmVersion == null ? "android-v1" : algorithmVersion;
    }

    public BodyMeasurement withSyncMetadata(String method, float measurementConfidence) {
        return new BodyMeasurement(
                recordId,
                measuredAt,
                heightMeters,
                shoulderWidthMeters,
                hipWidthMeters,
                armLengthMeters,
                legLengthMeters,
                bodyShape,
                recommendation,
                method,
                measurementConfidence,
                algorithmVersion
        );
    }
}
