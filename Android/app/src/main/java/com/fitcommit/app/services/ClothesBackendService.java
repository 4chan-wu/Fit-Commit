package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ClothesBackendService {
    public boolean isConfigured() {
        return ApiClient.BASE_URL != null
                && !ApiClient.BASE_URL.trim().isEmpty()
                && ApiClient.BACKEND_CLOTHES_USER_ID != null
                && !ApiClient.BACKEND_CLOTHES_USER_ID.trim().isEmpty();
    }

    public void save(
            String name,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("user_id", ApiClient.BACKEND_CLOTHES_USER_ID);
        payload.put("main_category", category);
        payload.put("subcategory", subcategory);
        payload.put("main_color", color);
        payload.put("season", seasonArray(season));
        payload.put("style_tags", tagsArray(styleTags));
        payload.put("image_url", result == null ? "" : result.imageUrl);
        payload.put("image_path", imagePath == null ? "" : imagePath);
        payload.put("confidence", result == null ? 0 : result.confidence);
        payload.put("analysis_method", result == null ? "clip" : result.method);
        payload.put("memo", "");

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(ApiClient.BASE_URL + "/clothes").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            String body = readResponse(connection);
            throw new IllegalStateException("Backend clothes save failed: " + statusCode + " " + body);
        }
    }

    private JSONArray seasonArray(String season) {
        JSONArray array = new JSONArray();
        if ("Spring/Summer".equals(season)) {
            array.put("spring");
            array.put("summer");
        } else if ("Fall/Winter".equals(season)) {
            array.put("fall");
            array.put("winter");
        } else if ("All-Season".equals(season)) {
            array.put("spring");
            array.put("summer");
            array.put("fall");
            array.put("winter");
        } else if (season != null && !season.trim().isEmpty()) {
            array.put(season.trim().toLowerCase());
        }
        return array;
    }

    private JSONArray tagsArray(String styleTags) {
        JSONArray array = new JSONArray();
        if (styleTags == null || styleTags.trim().isEmpty()) {
            return array;
        }
        for (String tag : styleTags.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                array.put(trimmed);
            }
        }
        return array;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        if (connection.getErrorStream() == null) {
            return "";
        }
        try (BufferedInputStream input = new BufferedInputStream(connection.getErrorStream())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }
}
