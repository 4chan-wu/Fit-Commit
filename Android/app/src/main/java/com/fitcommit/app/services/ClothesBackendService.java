package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;
import com.fitcommit.app.models.ClosetItem;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
        createCompleted(category, subcategory, color, season, styleTags, imagePath, result);
    }

    public JSONObject createPending(String category, String imagePath) throws Exception {
        String storageImagePath = localImageReference(imagePath);
        String storageImageUrl = "";
        try {
            System.out.println("[ClothesBackendService] Backend /clothes/upload request started category=" + category);
            JSONObject upload = uploadImage(category, imagePath);
            storageImagePath = upload.optString("image_path", storageImagePath);
            storageImageUrl = upload.optString("image_url", "");
            System.out.println("[ClothesBackendService] Storage upload succeeded path=" + storageImagePath);
        } catch (Exception error) {
            System.out.println("[ClothesBackendService] Storage upload failed, falling back to local image reference: " + error.getMessage());
        }

        JSONObject payload = new JSONObject();
        payload.put("user_id", ApiClient.BACKEND_CLOTHES_USER_ID);
        payload.put("main_category", category);
        payload.put("main_color", dominantColorName(imagePath));
        payload.put("analysis_method", "manual");
        payload.put("season", new JSONArray());
        payload.put("style_tags", new JSONArray());
        payload.put("image_url", storageImageUrl);
        payload.put("image_path", storageImagePath);
        payload.put("local_image_path", imagePath == null ? "" : imagePath);
        payload.put("analysis_status", "pending");
        payload.put("needs_analysis", true);
        payload.put("sync_status", "synced");
        payload.put("memo", "created_before_ai_analysis");
        return requestJson("POST", ApiClient.BASE_URL + "/clothes", payload);
    }

    private String localImageReference(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return "local://unknown";
        }
        File file = new File(imagePath);
        String name = file.getName();
        return "local://" + (name == null || name.trim().isEmpty() ? String.valueOf(System.currentTimeMillis()) : name);
    }

    private String dominantColorName(String imagePath) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                return "gray";
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            int pixel = scaled.getPixel(0, 0);
            int red = android.graphics.Color.red(pixel);
            int green = android.graphics.Color.green(pixel);
            int blue = android.graphics.Color.blue(pixel);
            String[] names = {"black", "white", "gray", "navy", "blue", "brown", "beige", "red", "pink", "green", "yellow", "purple"};
            int[][] values = {
                    {20, 20, 20}, {245, 245, 245}, {140, 140, 140}, {20, 35, 90},
                    {50, 100, 210}, {120, 80, 50}, {210, 190, 150}, {220, 40, 40},
                    {230, 130, 170}, {50, 150, 80}, {230, 210, 50}, {130, 80, 180}
            };
            String bestName = "gray";
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < names.length; i++) {
                double distance = Math.pow(red - values[i][0], 2)
                        + Math.pow(green - values[i][1], 2)
                        + Math.pow(blue - values[i][2], 2);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestName = names[i];
                }
            }
            return bestName;
        } catch (Exception ignored) {
            return "gray";
        }
    }

    public JSONObject uploadImage(String category, String imagePath) throws Exception {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Image path is empty.");
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imagePath);
        }

        String boundary = "fitcommit-" + java.util.UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(ApiClient.BASE_URL + "/clothes/upload").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        try (OutputStream output = connection.getOutputStream()) {
            writeFormField(output, boundary, "user_id", ApiClient.BACKEND_CLOTHES_USER_ID);
            writeFormField(output, boundary, "main_category", category);
            writeFormField(output, boundary, "image_type", "original");
            writeFileField(output, boundary, "file", file);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Backend upload failed: " + statusCode + " " + body);
        }
        return new JSONObject(body);
    }

    public JSONObject createCompleted(
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result
    ) throws Exception {
        JSONObject payload = completedPayload(category, subcategory, color, season, styleTags, imagePath, result);
        payload.put("user_id", ApiClient.BACKEND_CLOTHES_USER_ID);
        return requestJson("POST", ApiClient.BASE_URL + "/clothes", payload);
    }

    public JSONObject updateCompleted(
            String clothingId,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result
    ) throws Exception {
        JSONObject payload = completedPayload(category, subcategory, color, season, styleTags, imagePath, result);
        return requestJson("PUT", ApiClient.BASE_URL + "/clothes/" + clothingId, payload);
    }

    public JSONObject updateFailed(String clothingId, String imagePath, String errorMessage) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("image_path", imagePath == null ? "" : imagePath);
        payload.put("local_image_path", imagePath == null ? "" : imagePath);
        payload.put("analysis_status", "failed");
        payload.put("analysis_error", errorMessage == null ? "AI analysis failed" : errorMessage);
        payload.put("needs_analysis", true);
        payload.put("sync_status", "synced");
        payload.put("memo", "ai_analysis_failed");
        return requestJson("PUT", ApiClient.BASE_URL + "/clothes/" + clothingId, payload);
    }

    public List<ClosetItem> fetchClothes() throws Exception {
        String url = ApiClient.BASE_URL + "/clothes?user_id=" + ApiClient.BACKEND_CLOTHES_USER_ID;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

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
            String localImagePath = row.optString("local_image_path", imagePath);
            String status = row.optString("analysis_status", "completed");
            String syncStatus = row.optString("sync_status", "synced");
            boolean needsAnalysis = row.optBoolean("needs_analysis", !"completed".equals(status));
            String name = subcategory.isEmpty() ? category : subcategory;
            String note = "source=backend; status=" + status + "; style_tags=" + styleTags;
            if ("pending".equals(status)) {
                season = "분석 대기";
            } else if ("failed".equals(status)) {
                season = "분석 실패";
                note = note + "; retry_available=true";
            }
            items.add(new ClosetItem(
                    "",
                    String.valueOf(row.opt("id")),
                    name,
                    category,
                    color,
                    season,
                    note,
                    localImagePath,
                    status,
                    row.optString("analysis_error", ""),
                    needsAnalysis,
                    syncStatus,
                    "",
                    System.currentTimeMillis()
            ));
        }
        return items;
    }

    public JSONArray fetchWardrobes() throws Exception {
        String url = ApiClient.BASE_URL + "/wardrobes?user_id=" + ApiClient.BACKEND_CLOTHES_USER_ID;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Backend wardrobes fetch failed: " + statusCode + " " + body);
        }
        return new JSONArray(body);
    }

    public JSONObject fetchDefaultWardrobe() throws Exception {
        String url = ApiClient.BASE_URL + "/wardrobes/default?user_id=" + ApiClient.BACKEND_CLOTHES_USER_ID;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Backend default wardrobe fetch failed: " + statusCode + " " + body);
        }
        return new JSONObject(body);
    }

    private JSONObject completedPayload(
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("main_category", category);
        payload.put("subcategory", subcategory);
        payload.put("main_color", color);
        payload.put("season", seasonArray(season));
        payload.put("style_tags", tagsArray(styleTags));
        payload.put("image_url", result == null ? "" : result.imageUrl);
        payload.put("image_path", imagePath == null ? "" : imagePath);
        payload.put("local_image_path", imagePath == null ? "" : imagePath);
        payload.put("confidence", result == null ? 0 : result.confidence);
        boolean manual = result == null || result.confidence <= 0;
        payload.put("analysis_method", manual ? "manual" : result.method);
        payload.put("analysis_status", manual ? "pending" : "completed");
        payload.put("analysis_error", "");
        if (!manual) {
            payload.put("analyzed_at", isoNow());
        }
        payload.put("needs_analysis", manual);
        payload.put("sync_status", "synced");
        payload.put("memo", "");
        return payload;
    }

    private JSONObject requestJson(String method, String url, JSONObject payload) throws Exception {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Backend clothes request failed: " + statusCode + " " + body);
        }
        return new JSONObject(body);
    }

    private void writeFormField(OutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(OutputStream output, String boundary, String name, File file) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String isoNow() {
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return formatter.format(new java.util.Date());
    }

    private JSONArray seasonArray(String season) {
        JSONArray array = new JSONArray();
        if ("All-Season".equals(season)) {
            array.put("spring");
            array.put("summer");
            array.put("fall");
            array.put("winter");
        } else if (season != null && !season.trim().isEmpty()) {
            for (String value : season.split("[/, ]+")) {
                String normalized = value.trim().toLowerCase();
                if (!normalized.isEmpty()) {
                    array.put(normalized);
                }
            }
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

    private void applyAuthHeaders(HttpURLConnection connection) {
        if (ApiClient.SUPABASE_ACCESS_TOKEN != null && !ApiClient.SUPABASE_ACCESS_TOKEN.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + ApiClient.SUPABASE_ACCESS_TOKEN);
        }
        if (ApiClient.BACKEND_CLOTHES_USER_ID != null && !ApiClient.BACKEND_CLOTHES_USER_ID.trim().isEmpty()) {
            connection.setRequestProperty("X-Test-User-Id", ApiClient.BACKEND_CLOTHES_USER_ID);
        }
    }
}
