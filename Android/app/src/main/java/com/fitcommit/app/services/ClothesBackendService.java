package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;
import com.fitcommit.app.models.ClosetItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            String body = readResponse(connection, statusCode);
            throw new IllegalStateException("Backend clothes save failed: " + statusCode + " " + body);
        }
    }

    public List<ClosetItem> fetchClothes() throws Exception {
        String url = ApiClient.BASE_URL + "/clothes?user_id=" + ApiClient.BACKEND_CLOTHES_USER_ID;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Backend clothes fetch failed: " + statusCode + " " + body);
        }

        JSONArray rows = new JSONArray(body);
        List<ClosetItem> items = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String category = row.optString("main_category", "top");
            String subcategory = row.optString("subcategory", "");
            String color = row.optString("main_color", "#CCCCCC");
            String season = seasonLabel(row.optJSONArray("season"));
            String styleTags = joinTags(row.optJSONArray("style_tags"));
            String imagePath = row.optString("image_path", "");
            String name = subcategory.isEmpty() ? category : subcategory;
            String note = "source=backend; style_tags=" + styleTags;
            items.add(new ClosetItem(name, category, color, season, note, imagePath));
        }
        return items;
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

    private String seasonLabel(JSONArray seasons) {
        if (seasons == null) {
            return "All-Season";
        }
        boolean spring = contains(seasons, "spring");
        boolean summer = contains(seasons, "summer");
        boolean fall = contains(seasons, "fall");
        boolean winter = contains(seasons, "winter");
        if (fall && winter) {
            return "Fall/Winter";
        }
        if (spring && summer) {
            return "Spring/Summer";
        }
        if (seasons.length() >= 4) {
            return "All-Season";
        }
        return seasons.length() > 0 ? seasons.optString(0) : "All-Season";
    }

    private boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private String joinTags(JSONArray tags) {
        if (tags == null || tags.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.length(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(tags.optString(i));
        }
        return builder.toString();
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        java.io.InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (BufferedInputStream input = new BufferedInputStream(stream)) {
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
