package com.fitcommit.app.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.fitcommit.app.models.BodyMeasurement;

import org.json.JSONArray;
import org.json.JSONObject;

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
            updated.put(existing.opt(index));
        }
        preferences.edit().putString(key, updated.toString()).apply();
    }

    public static BodyMeasurement latest(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray measurements = new JSONArray(preferences.getString(keyForCurrentUser(context), "[]"));
            JSONObject object = measurements.optJSONObject(0);
            if (object == null) return null;
            return new BodyMeasurement(
                    object.optLong("measured_at"),
                    (float) object.optDouble("height_m"),
                    (float) object.optDouble("shoulder_width_m"),
                    (float) object.optDouble("hip_width_m"),
                    (float) object.optDouble("arm_length_m"),
                    (float) object.optDouble("leg_length_m"),
                    object.optString("body_shape"),
                    object.optString("recommendation")
            );
        } catch (Exception ignored) {
            return null;
        }
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
            object.put("height_m", measurement.heightMeters);
            object.put("shoulder_width_m", measurement.shoulderWidthMeters);
            object.put("hip_width_m", measurement.hipWidthMeters);
            object.put("arm_length_m", measurement.armLengthMeters);
            object.put("leg_length_m", measurement.legLengthMeters);
            object.put("body_shape", measurement.bodyShape);
            object.put("recommendation", measurement.recommendation);
        } catch (Exception ignored) {
        }
        return object;
    }
}
