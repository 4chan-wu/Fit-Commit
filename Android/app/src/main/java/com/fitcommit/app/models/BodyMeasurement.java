package com.fitcommit.app.models;

public final class BodyMeasurement {
    public final long measuredAt;
    public final float heightMeters;
    public final float shoulderWidthMeters;
    public final float hipWidthMeters;
    public final float armLengthMeters;
    public final float legLengthMeters;
    public final String bodyShape;
    public final String recommendation;

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
        this.measuredAt = measuredAt;
        this.heightMeters = heightMeters;
        this.shoulderWidthMeters = shoulderWidthMeters;
        this.hipWidthMeters = hipWidthMeters;
        this.armLengthMeters = armLengthMeters;
        this.legLengthMeters = legLengthMeters;
        this.bodyShape = bodyShape;
        this.recommendation = recommendation;
    }
}
