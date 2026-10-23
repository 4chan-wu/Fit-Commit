package com.fitcommit.app.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.fitcommit.app.models.BodyMeasurement;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class BodyMeasurementStorage {
    private static final String PREFS_NAME = "fitcommit_body_measurements";
    private static final int MAX_SAVED_MEASUREMENTS = 20;

    private BodyMeasurementStorage() {
    }

    public static void save(Context context, BodyMeasurement measurement) {
        String key = keyForCurrentUser(context);
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray existing;
        try {
            existing = new JSONArray(preferences.getString(key, "[]"));
        } catch (Exception ignored) {
            existing = new JSONArray();
        }

        JSONArray updated = new JSONArray();
        updated.put(toJson(measurement));
        for (int index = 0; index < existing.length() && updated.length() < MAX_SAVED_MEASUREMENTS; index++) {
            JSONObject previous = existing.optJSONObject(index);
            if (previous == null
                    || Math.abs(previous.optLong("measured_at") - measurement.measuredAt) < 1_000L) continue;
            updated.put(previous);
        }
        preferences.edit().putString(key, updated.toString()).apply();
    }

    public static BodyMeasurement latest(Context context) {
        List<BodyMeasurement> measurements = all(context);
        return measurements.isEmpty() ? null : measurements.get(0);
    }

    public static List<BodyMeasurement> all(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<BodyMeasurement> results = new ArrayList<>();
        try {
            JSONArray measurements = new JSONArray(preferences.getString(keyForCurrentUser(context), "[]"));
            for (int index = 0; index < measurements.length(); index++) {
                JSONObject object = measurements.optJSONObject(index);
                BodyMeasurement measurement = fromJson(object);
                if (measurement != null) results.add(measurement);
            }
        } catch (Exception ignored) {
            // A malformed local cache must not prevent a new measurement.
        }
        results.sort((first, second) -> Long.compare(second.measuredAt, first.measuredAt));
        return results;
    }

    public static void replaceAll(Context context, List<BodyMeasurement> measurements) {
        JSONArray array = new JSONArray();
        List<BodyMeasurement> sorted = new ArrayList<>(measurements);
        sorted.sort((first, second) -> Long.compare(second.measuredAt, first.measuredAt));
        for (int index = 0; index < sorted.size() && index < MAX_SAVED_MEASUREMENTS; index++) {
            array.put(toJson(sorted.get(index)));
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(keyForCurrentUser(context), array.toString())
                .apply();
    }

    public static void clearAll(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String keyForCurrentUser(Context context) {
        String userId = new TokenManager(context).getUserId();
        return "measurements_" + (userId == null ? "local" : userId);
    }

    private static JSONObject toJson(BodyMeasurement measurement) {
        JSONObject object = new JSONObject();
        try {
            object.put("measured_at", measurement.measuredAt);
            object.put("record_id", measurement.recordId);
            object.put("height_m", measurement.heightMeters);
            object.put("shoulder_width_m", measurement.shoulderWidthMeters);
            object.put("hip_width_m", measurement.hipWidthMeters);
            object.put("arm_length_m", measurement.armLengthMeters);
            object.put("leg_length_m", measurement.legLengthMeters);
            object.put("body_shape", measurement.bodyShape);
            object.put("recommendation", measurement.recommendation);
            object.put("measurement_method", measurement.measurementMethod);
            object.put("confidence", measurement.confidence);
            object.put("algorithm_version", measurement.algorithmVersion);
        } catch (Exception ignored) {
        }
        return object;
    }

    private static BodyMeasurement fromJson(JSONObject object) {
        if (object == null) return null;
        return new BodyMeasurement(
                object.optString("record_id", ""),
                object.optLong("measured_at"),
                (float) object.optDouble("height_m"),
                (float) object.optDouble("shoulder_width_m"),
                (float) object.optDouble("hip_width_m"),
                (float) object.optDouble("arm_length_m"),
                (float) object.optDouble("leg_length_m"),
                object.optString("body_shape"),
                object.optString("recommendation"),
                object.optString("measurement_method", ""),
                (float) object.optDouble("confidence", 0d),
                object.optString("algorithm_version", "android-v1")
        );
    }
}
