package com.fitcommit.app.services;

import android.content.Context;

import com.fitcommit.app.models.BodyMeasurement;
import com.fitcommit.app.services.dto.LoginResponse;
import com.fitcommit.app.services.dto.RefreshTokenRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class BodyMeasurementService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final TokenManager tokenManager;

    public BodyMeasurementService(Context context) {
        tokenManager = new TokenManager(context.getApplicationContext());
    }

    public boolean isConfigured() {
        return ApiClient.isConfigured() && tokenManager.hasSupabaseSession();
    }

    public BodyMeasurement save(BodyMeasurement measurement) throws Exception {
        requireSession();
        JSONObject payload = new JSONObject();
        payload.put("user_id", tokenManager.getUserId());
        payload.put("measured_at", toIsoTime(measurement.measuredAt));
        payload.put("height_cm", centimeters(measurement.heightMeters));
        payload.put("shoulder_width_cm", centimeters(measurement.shoulderWidthMeters));
        payload.put("hip_width_cm", centimeters(measurement.hipWidthMeters));
        payload.put("arm_length_cm", centimeters(measurement.armLengthMeters));
        payload.put("leg_length_cm", centimeters(measurement.legLengthMeters));
        payload.put("body_shape_code", shapeCode(measurement.bodyShape));
        payload.put("measurement_method", normalizedMethod(measurement.measurementMethod));
        payload.put("confidence", Math.max(0f, Math.min(1f, measurement.confidence)));
        payload.put("algorithm_version", measurement.algorithmVersion);

        JSONArray rows = request(
                "POST",
                ApiClient.BASE_URL + "/body_measurements",
                payload,
                "return=representation"
        );
        if (rows.length() == 0) throw new IllegalStateException("측정 기록 저장 응답이 비어 있습니다.");
        return fromRow(rows.getJSONObject(0));
    }

    public List<BodyMeasurement> fetchAll() throws Exception {
        requireSession();
        JSONArray rows = request(
                "GET",
                ApiClient.BASE_URL + "/body_measurements?select=*&order=measured_at.desc&limit=100",
                null,
                null
        );
        List<BodyMeasurement> measurements = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            measurements.add(fromRow(rows.getJSONObject(index)));
        }
        return measurements;
    }

    public List<BodyMeasurement> fetchAndSyncLocal(List<BodyMeasurement> local) throws Exception {
        List<BodyMeasurement> server = fetchAll();
        for (BodyMeasurement localMeasurement : local) {
            if (containsTimestamp(server, localMeasurement.measuredAt)) continue;
            try {
                String method = localMeasurement.measurementMethod.trim().isEmpty()
                        ? "ar_raw_depth"
                        : localMeasurement.measurementMethod;
                BodyMeasurement uploaded = save(localMeasurement.withSyncMetadata(
                        method,
                        localMeasurement.confidence
                ));
                server.add(uploaded);
            } catch (Exception ignored) {
                // Keep this record in the local merge and retry on the next history refresh.
            }
        }
        return mergeWithLocal(server, local);
    }

    public static List<BodyMeasurement> mergeWithLocal(
            List<BodyMeasurement> server,
            List<BodyMeasurement> local
    ) {
        List<BodyMeasurement> merged = new ArrayList<>(server);
        for (BodyMeasurement localMeasurement : local) {
            boolean duplicate = false;
            for (BodyMeasurement serverMeasurement : server) {
                if (Math.abs(serverMeasurement.measuredAt - localMeasurement.measuredAt) < 1_000L) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) merged.add(localMeasurement);
        }
        merged.sort((first, second) -> Long.compare(second.measuredAt, first.measuredAt));
        return merged;
    }

    private static boolean containsTimestamp(List<BodyMeasurement> measurements, long measuredAt) {
        for (BodyMeasurement measurement : measurements) {
            if (Math.abs(measurement.measuredAt - measuredAt) < 1_000L) return true;
        }
        return false;
    }

    private JSONArray request(String method, String url, JSONObject payload, String prefer) throws Exception {
        refreshSessionIfNeeded();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenManager.getAccessToken());
        if (prefer != null) builder.header("Prefer", prefer);
        if ("GET".equals(method)) {
            builder.get();
        } else {
            builder.method(method, RequestBody.create(JSON, payload == null ? "{}" : payload.toString()));
        }
        try (Response response = ApiClient.getHttpClient().newCall(builder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("신체 기록 서버 요청 실패: " + response.code() + " " + body);
            }
            return body.trim().isEmpty() ? new JSONArray() : new JSONArray(body);
        }
    }

    private BodyMeasurement fromRow(JSONObject row) {
        String code = row.optString("body_shape_code", "unknown");
        return new BodyMeasurement(
                row.optString("id", ""),
                parseIsoTime(row.optString("measured_at", "")),
                meters(row.optDouble("height_cm")),
                meters(row.optDouble("shoulder_width_cm")),
                meters(row.optDouble("hip_width_cm")),
                meters(row.optDouble("arm_length_cm")),
                meters(row.optDouble("leg_length_cm")),
                code,
                "",
                row.optString("measurement_method", ""),
                (float) row.optDouble("confidence", 0d),
                row.optString("algorithm_version", "android-v1")
        );
    }

    private void requireSession() {
        String userId = tokenManager.getUserId();
        if (!isConfigured() || userId == null || userId.trim().isEmpty()) {
            throw new IllegalStateException("Supabase 로그인이 필요합니다.");
        }
    }

    private void refreshSessionIfNeeded() throws Exception {
        long now = System.currentTimeMillis() / 1_000L;
        long expiresAt = tokenManager.getExpiresAtEpochSeconds();
        if (expiresAt == 0L || expiresAt > now + 60L) return;
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalStateException("로그인 세션이 만료되었습니다.");
        }
        retrofit2.Response<LoginResponse> response = ApiClient.getRetrofit()
                .create(AuthApi.class)
                .refresh("refresh_token", new RefreshTokenRequest(refreshToken))
                .execute();
        LoginResponse session = response.body();
        if (!response.isSuccessful() || session == null || session.getAccessToken() == null) {
            throw new IllegalStateException("로그인 세션을 갱신할 수 없습니다.");
        }
        tokenManager.saveSupabaseSession(
                session.getAccessToken(),
                session.getRefreshToken(),
                session.getExpiresAt(),
                session.getUser() == null ? tokenManager.getUserId() : session.getUser().getId()
        );
    }

    private static double centimeters(float meters) {
        return Math.round(meters * 10_000d) / 100d;
    }

    private static float meters(double centimeters) {
        return (float) (centimeters / 100d);
    }

    private static String normalizedMethod(String method) {
        return "manual_height".equals(method) ? "manual_height" : "ar_raw_depth";
    }

    private static String shapeCode(String value) {
        if ("inverted_triangle".equals(value) || "역삼각형".equals(value)) return "inverted_triangle";
        if ("triangle".equals(value) || "삼각형".equals(value)) return "triangle";
        if ("rectangle".equals(value) || "직사각형/스탠다드".equals(value)) return "rectangle";
        return "unknown";
    }

    private static String toIsoTime(long epochMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date(epochMillis));
    }

    private static long parseIsoTime(String value) {
        String normalized = normalizeFractionalSeconds(value);
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"};
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.US).parse(normalized).getTime();
            } catch (Exception ignored) {
                // Supabase may return timestamps with different fractional precision.
            }
        }
        return System.currentTimeMillis();
    }

    private static String normalizeFractionalSeconds(String value) {
        if (value == null) return "";
        int dot = value.indexOf('.');
        if (dot < 0) return value;
        int zone = value.indexOf('Z', dot);
        if (zone < 0) zone = value.indexOf('+', dot);
        if (zone < 0) zone = value.indexOf('-', dot);
        if (zone < 0) return value;
        String fraction = value.substring(dot + 1, zone);
        if (fraction.length() > 3) fraction = fraction.substring(0, 3);
        while (fraction.length() < 3) fraction += "0";
        return value.substring(0, dot + 1) + fraction + value.substring(zone);
    }
}
