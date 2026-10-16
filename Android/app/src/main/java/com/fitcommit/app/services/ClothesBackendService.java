package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.models.Wardrobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ClothesBackendService {
    private final Context context;
    private final TokenManager tokenManager;

    public ClothesBackendService(Context context) {
        this.context = context.getApplicationContext();
        tokenManager = new TokenManager(this.context);
    }

    public boolean isConfigured() {
        return ApiClient.isConfigured() && tokenManager.hasSupabaseSession();
    }

    public boolean isUsingAuthenticatedUser() {
        return tokenManager.hasSupabaseSession();
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
        createCompleted(name, category, subcategory, color, season, styleTags, imagePath, null, result, "");
    }

    public JSONObject createPending(String category, String imagePath) throws Exception {
        return createPending(category, imagePath, "");
    }

    public JSONObject createPending(String category, String imagePath, String wardrobeId) throws Exception {
        return createPending(category, imagePath, null, wardrobeId);
    }

    public JSONObject createPending(
            String category,
            String originalImagePath,
            String processedImagePath,
            String wardrobeId
    ) throws Exception {
        String storageImagePath = localImageReference(originalImagePath);
        String backgroundRemovedImagePath = "";
        try {
            System.out.println("[ClothesBackendService] Supabase Storage upload started category=" + category);
            JSONObject upload = uploadImage(category, originalImagePath, "original");
            storageImagePath = upload.optString("image_path", storageImagePath);
            if (processedImagePath != null && !processedImagePath.trim().isEmpty()) {
                JSONObject cutoutUpload = uploadImage(category, processedImagePath, "background_removed");
                backgroundRemovedImagePath = cutoutUpload.optString("image_path", "");
            }
            System.out.println("[ClothesBackendService] Storage upload succeeded path=" + storageImagePath);
        } catch (Exception error) {
            System.out.println("[ClothesBackendService] Storage upload failed, falling back to local image reference: " + error.getMessage());
        }

        JSONObject payload = new JSONObject();
        putCurrentUserId(payload);
        if (wardrobeId != null && !wardrobeId.trim().isEmpty()) {
            payload.put("wardrobe_id", wardrobeId);
        }
        payload.put("main_category", category);
        String colorSourcePath = processedImagePath == null || processedImagePath.trim().isEmpty()
                ? originalImagePath
                : processedImagePath;
        payload.put("main_color", dominantColorName(colorSourcePath));
        payload.put("analysis_method", "manual");
        payload.put("season", new JSONArray());
        payload.put("style_tags", new JSONArray());
        payload.put("image_url", "");
        payload.put("image_path", storageImagePath);
        payload.put("local_image_path", "");
        if (!backgroundRemovedImagePath.isEmpty()) {
            payload.put("background_removed_image_path", backgroundRemovedImagePath);
            payload.put("background_removed_image_url", "");
            payload.put("background_removal_status", "completed");
            payload.put("background_removal_error", JSONObject.NULL);
        } else {
            payload.put("background_removal_status", "not_requested");
        }
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
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true);
            long redTotal = 0;
            long greenTotal = 0;
            long blueTotal = 0;
            long alphaTotal = 0;
            for (int y = 0; y < scaled.getHeight(); y++) {
                for (int x = 0; x < scaled.getWidth(); x++) {
                    int pixel = scaled.getPixel(x, y);
                    int alpha = android.graphics.Color.alpha(pixel);
                    if (alpha < 32) continue;
                    redTotal += (long) android.graphics.Color.red(pixel) * alpha;
                    greenTotal += (long) android.graphics.Color.green(pixel) * alpha;
                    blueTotal += (long) android.graphics.Color.blue(pixel) * alpha;
                    alphaTotal += alpha;
                }
            }
            if (scaled != bitmap) scaled.recycle();
            bitmap.recycle();
            if (alphaTotal == 0) return "gray";
            int red = (int) (redTotal / alphaTotal);
            int green = (int) (greenTotal / alphaTotal);
            int blue = (int) (blueTotal / alphaTotal);
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
        return uploadImage(category, imagePath, "original");
    }

    public JSONObject uploadImage(String category, String imagePath, String imageType) throws Exception {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Image path is empty.");
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imagePath);
        }

        requireAuthenticatedUserId();
        boolean backgroundRemoved = "background_removed".equals(imageType);
        String extension = backgroundRemoved ? "webp" : "jpg";
        String typeFolder = backgroundRemoved ? "/background_removed" : "";
        String objectPath = tokenManager.getUserId() + "/" + safePathSegment(category) + typeFolder + "/"
                + java.util.UUID.randomUUID() + "." + extension;
        String encodedPath = encodeStoragePath(objectPath);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                ApiClient.STORAGE_URL + "/object/" + ApiClient.SUPABASE_CLOTHING_BUCKET + "/" + encodedPath
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", backgroundRemoved ? "image/webp" : "image/jpeg");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("x-upsert", "false");
        applyAuthHeaders(connection);

        byte[] optimizedImage = backgroundRemoved ? readFileWithLimit(file) : optimizedJpeg(file);
        connection.setFixedLengthStreamingMode(optimizedImage.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(optimizedImage);
        }

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase Storage upload failed: " + statusCode + " " + body);
        }
        JSONObject result = new JSONObject();
        result.put("image_path", objectPath);
        result.put("image_url", "");
        result.put("bucket", ApiClient.SUPABASE_CLOTHING_BUCKET);
        return result;
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
        return createCompleted(null, category, subcategory, color, season, styleTags, imagePath, null, result, "");
    }

    public JSONObject createCompleted(
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result,
            String wardrobeId
    ) throws Exception {
        return createCompleted(null, category, subcategory, color, season, styleTags, imagePath, null, result, wardrobeId);
    }

    public JSONObject createCompleted(
            String displayName,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String originalImagePath,
            String processedImagePath,
            AnalysisResult result,
            String wardrobeId
    ) throws Exception {
        JSONObject originalUpload = uploadImage(category, originalImagePath, "original");
        JSONObject cutoutUpload = processedImagePath == null || processedImagePath.trim().isEmpty()
                ? null
                : uploadImage(category, processedImagePath, "background_removed");
        JSONObject payload = completedPayload(displayName, category, subcategory, color, season, styleTags, result, wardrobeId);
        payload.put("image_path", originalUpload.optString("image_path"));
        payload.put("image_url", "");
        payload.put("local_image_path", "");
        if (cutoutUpload != null) {
            payload.put("background_removed_image_path", cutoutUpload.optString("image_path"));
            payload.put("background_removed_image_url", "");
            payload.put("background_removal_status", "completed");
            payload.put("background_removal_error", JSONObject.NULL);
        } else {
            payload.put("background_removal_status", "not_requested");
        }
        putCurrentUserId(payload);
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
        return updateCompleted(clothingId, null, category, subcategory, color, season, styleTags, imagePath, result, "");
    }

    public JSONObject updateCompleted(
            String clothingId,
            String displayName,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            String imagePath,
            AnalysisResult result,
            String wardrobeId
    ) throws Exception {
        JSONObject payload = completedPayload(displayName, category, subcategory, color, season, styleTags, result, wardrobeId);
        return requestJson("PATCH", ApiClient.BASE_URL + "/clothes?id=eq." + urlEncode(clothingId), payload);
    }

    public JSONObject moveClothing(String clothingId, String wardrobeId) throws Exception {
        if (clothingId == null || clothingId.trim().isEmpty()) {
            throw new IllegalArgumentException("이동할 의류 ID가 없습니다.");
        }
        if (wardrobeId == null || wardrobeId.trim().isEmpty()) {
            throw new IllegalArgumentException("이동할 옷장을 선택해주세요.");
        }
        JSONObject payload = new JSONObject();
        payload.put("wardrobe_id", wardrobeId);
        return requestJson("PATCH", ApiClient.BASE_URL + "/clothes?id=eq." + urlEncode(clothingId), payload);
    }

    public JSONObject updateFailed(String clothingId, String imagePath, String errorMessage) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("analysis_status", "failed");
        payload.put("analysis_error", errorMessage == null ? "AI analysis failed" : errorMessage);
        payload.put("needs_analysis", true);
        payload.put("sync_status", "synced");
        payload.put("memo", "ai_analysis_failed");
        return requestJson("PATCH", ApiClient.BASE_URL + "/clothes?id=eq." + urlEncode(clothingId), payload);
    }

    public boolean deleteClothing(String clothingId) throws Exception {
        if (clothingId == null || clothingId.trim().isEmpty()) {
            throw new IllegalArgumentException("삭제할 서버 의류 ID가 없습니다.");
        }
        requireAuthenticatedUserId();
        String filter = "?id=eq." + urlEncode(clothingId);
        List<String> storagePaths = fetchClothingStoragePaths(filter);

        HttpURLConnection connection = (HttpURLConnection) new URL(ApiClient.BASE_URL + "/clothes" + filter).openConnection();
        connection.setRequestMethod("DELETE");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        applyAuthHeaders(connection);
        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase 의류 삭제 실패: " + statusCode + " " + body);
        }
        JSONArray deletedRows = body == null || body.trim().isEmpty() ? new JSONArray() : new JSONArray(body);
        if (deletedRows.length() == 0) {
            throw new IllegalStateException("삭제할 의류를 찾지 못했습니다.");
        }

        if (!storagePaths.isEmpty()) {
            try {
                deleteStorageObjects(storagePaths);
            } catch (Exception cleanupError) {
                System.out.println("[ClothesBackendService] Deleted clothing row, but image cleanup failed: "
                        + cleanupError.getMessage());
                return false;
            }
        }
        return true;
    }

    private List<String> fetchClothingStoragePaths(String filter) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                ApiClient.BASE_URL + "/clothes" + filter
                        + "&select=image_path,background_removed_image_path&limit=1"
        ).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);
        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("삭제할 의류 조회 실패: " + statusCode + " " + body);
        }
        List<String> paths = new ArrayList<>();
        JSONArray rows = new JSONArray(body);
        if (rows.length() == 0) return paths;
        JSONObject row = rows.getJSONObject(0);
        addStoragePath(paths, row.optString("image_path", ""));
        addStoragePath(paths, row.optString("background_removed_image_path", ""));
        return paths;
    }

    private void addStoragePath(List<String> paths, String path) {
        if (path != null && !path.trim().isEmpty() && !path.startsWith("local://")) {
            paths.add(path);
        }
    }

    private void deleteStorageObjects(List<String> paths) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                ApiClient.STORAGE_URL + "/object/" + ApiClient.SUPABASE_CLOTHING_BUCKET
        ).openConnection();
        connection.setRequestMethod("DELETE");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);
        JSONObject payload = new JSONObject();
        payload.put("prefixes", new JSONArray(paths));
        byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bodyBytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bodyBytes);
        }
        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase 이미지 삭제 실패: " + statusCode + " " + body);
        }
    }

    public List<ClosetItem> fetchClothes() throws Exception {
        return fetchClothes("");
    }

    public List<ClosetItem> fetchClothes(String wardrobeId) throws Exception {
        requireAuthenticatedUserId();
        String url = ApiClient.BASE_URL + "/clothes?select=*&order=created_at.desc";
        if (wardrobeId != null && !wardrobeId.trim().isEmpty()) {
            url += "&wardrobe_id=eq." + urlEncode(wardrobeId);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase clothes fetch failed: " + statusCode + " " + body);
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
            String originalStoragePath = row.optString("image_path", "");
            String cutoutStoragePath = row.optString("background_removed_image_path", "");
            String localImagePath = row.optString("local_image_path", "");
            if (localImagePath.isEmpty() || !new File(localImagePath).exists()) {
                String preferredStoragePath = cutoutStoragePath.isEmpty() ? originalStoragePath : cutoutStoragePath;
                localImagePath = downloadStorageImage(preferredStoragePath);
            }
            String status = row.optString("analysis_status", "completed");
            String syncStatus = row.optString("sync_status", "synced");
            String wardrobeIdValue = row.optString("wardrobe_id", "");
            boolean needsAnalysis = row.optBoolean("needs_analysis", !"completed".equals(status));
            String fallbackName = subcategory.isEmpty() ? category : subcategory;
            String name = row.optString("display_name", fallbackName);
            if (name.trim().isEmpty()) name = fallbackName;
            String note = "source=backend; status=" + status + "; subcategory=" + subcategory
                    + "; style_tags=" + styleTags;
            if ("pending".equals(status)) {
                season = "분석 대기";
            } else if ("failed".equals(status)) {
                season = "분석 실패";
                note = note + "; retry_available=true";
            }
            items.add(new ClosetItem(
                    "",
                    String.valueOf(row.opt("id")),
                    wardrobeIdValue,
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
        requireAuthenticatedUserId();
        String url = ApiClient.BASE_URL + "/wardrobes?select=*&order=is_default.desc,created_at.asc";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        applyAuthHeaders(connection);

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase wardrobes fetch failed: " + statusCode + " " + body);
        }
        return new JSONArray(body);
    }

    public JSONObject fetchDefaultWardrobe() throws Exception {
        requireAuthenticatedUserId();
        String url = ApiClient.BASE_URL + "/wardrobes?select=*&is_default=eq.true&limit=1";
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
        JSONArray rows = new JSONArray(body);
        if (rows.length() == 0) throw new IllegalStateException("기본 옷장이 없습니다.");
        return rows.getJSONObject(0);
    }

    public List<Wardrobe> fetchWardrobeModels() throws Exception {
        JSONArray rows = fetchWardrobes();
        List<Wardrobe> wardrobes = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            wardrobes.add(new Wardrobe(
                    row.optString("id", ""),
                    row.optString("user_id", ""),
                    row.optString("name", "기본 옷장"),
                    row.optString("description", ""),
                    row.optBoolean("is_default", false)
            ));
        }
        return wardrobes;
    }

    public JSONObject createWardrobe(String name, String description) throws Exception {
        JSONObject payload = new JSONObject();
        putCurrentUserId(payload);
        payload.put("name", name);
        payload.put("description", description == null ? "" : description);
        payload.put("is_default", false);
        return requestJson("POST", ApiClient.BASE_URL + "/wardrobes", payload);
    }

    public JSONObject updateWardrobe(String wardrobeId, String name, String description) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("name", name);
        payload.put("description", description == null ? "" : description);
        return requestJson("PATCH", wardrobeMutationUrl(wardrobeId), payload);
    }

    public void deleteWardrobe(String wardrobeId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("p_wardrobe_id", wardrobeId);
        requestJson("POST", ApiClient.BASE_URL + "/rpc/delete_wardrobe_and_move_clothes", payload);
    }

    private JSONObject completedPayload(
            String displayName,
            String category,
            String subcategory,
            String color,
            String season,
            String styleTags,
            AnalysisResult result,
            String wardrobeId
    ) throws Exception {
        JSONObject payload = new JSONObject();
        if (displayName != null) {
            payload.put("display_name", displayName.trim());
        }
        if (wardrobeId != null && !wardrobeId.trim().isEmpty()) {
            payload.put("wardrobe_id", wardrobeId);
        }
        payload.put("main_category", category);
        payload.put("subcategory", subcategory);
        payload.put("main_color", color);
        payload.put("season", seasonArray(season));
        payload.put("style_tags", tagsArray(styleTags));
        payload.put("confidence", result == null ? 0 : result.confidence);
        boolean manual = result == null || result.confidence <= 0;
        payload.put("analysis_method", manual ? "manual" : result.method);
        payload.put("analysis_status", "completed");
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
        refreshSessionIfNeeded();
        RequestBody requestBody = RequestBody.create(
                MediaType.get("application/json; charset=utf-8"),
                payload.toString()
        );
        Request request = new Request.Builder()
                .url(url)
                .method(method, requestBody)
                .header("Accept", "application/json")
                .header("Prefer", "return=representation")
                .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                .build();
        int statusCode;
        String body;
        try (Response response = ApiClient.getHttpClient().newCall(request).execute()) {
            statusCode = response.code();
            body = response.body() == null ? "" : response.body().string();
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Supabase request failed: " + statusCode + " " + body);
        }
        if (body == null || body.trim().isEmpty()) return new JSONObject();
        if (body.trim().startsWith("[")) {
            JSONArray rows = new JSONArray(body);
            if (rows.length() == 0) throw new IllegalStateException("Supabase 응답에 변경된 데이터가 없습니다.");
            return rows.getJSONObject(0);
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
        if (seasons.length() >= 4) {
            return "All-Season";
        }
        if (fall && winter) {
            return "Fall/Winter";
        }
        if (spring && summer) {
            return "Spring/Summer";
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

    private void applyAuthHeaders(HttpURLConnection connection) throws Exception {
        if (tokenManager.hasSupabaseSession()) {
            refreshSessionIfNeeded();
            connection.setRequestProperty("Authorization", "Bearer " + tokenManager.getAccessToken());
        }
        connection.setRequestProperty("apikey", ApiClient.SUPABASE_ANON_KEY);
    }

    private void refreshSessionIfNeeded() throws Exception {
        long expiresAt = tokenManager.getExpiresAtEpochSeconds();
        long now = System.currentTimeMillis() / 1000L;
        if (expiresAt == 0L || expiresAt > now + 60L) {
            return;
        }

        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            tokenManager.clear();
            throw new IllegalStateException("로그인 세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(ApiClient.AUTH_URL + "/token?grant_type=refresh_token").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("apikey", ApiClient.SUPABASE_ANON_KEY);

        JSONObject payload = new JSONObject();
        payload.put("refresh_token", refreshToken);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        String body = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            tokenManager.clear();
            throw new IllegalStateException("로그인 세션을 갱신할 수 없습니다. 다시 로그인해주세요.");
        }

        JSONObject session = new JSONObject(body);
        tokenManager.saveSupabaseSession(
                session.getString("access_token"),
                session.getString("refresh_token"),
                session.optLong("expires_at", now + 3600L),
                session.optJSONObject("user") == null
                        ? tokenManager.getUserId()
                        : session.optJSONObject("user").optString("id", tokenManager.getUserId())
        );
    }

    private String requireAuthenticatedUserId() {
        String userId = tokenManager.getUserId();
        if (!tokenManager.hasSupabaseSession() || userId == null || userId.trim().isEmpty()) {
            throw new IllegalStateException("Supabase 로그인이 필요합니다.");
        }
        return userId;
    }

    private void putCurrentUserId(JSONObject payload) throws Exception {
        payload.put("user_id", requireAuthenticatedUserId());
    }

    private String wardrobeMutationUrl(String wardrobeId) {
        return ApiClient.BASE_URL + "/wardrobes?id=eq." + urlEncode(wardrobeId);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private String safePathSegment(String value) {
        String normalized = value == null ? "uncategorized" : value.replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.isEmpty() ? "uncategorized" : normalized;
    }

    private String encodeStoragePath(String path) {
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(urlEncode(segment).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private byte[] optimizedJpeg(File file) throws Exception {
        Bitmap source = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (source == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        Bitmap outputBitmap = source;
        if (longest > 1600) {
            float ratio = 1600f / longest;
            outputBitmap = Bitmap.createScaledBitmap(
                    source,
                    Math.max(1, Math.round(width * ratio)),
                    Math.max(1, Math.round(height * ratio)),
                    true
            );
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 82, output);
        if (outputBitmap != source) outputBitmap.recycle();
        source.recycle();
        byte[] bytes = output.toByteArray();
        if (bytes.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("최적화된 이미지가 2MB를 초과합니다.");
        }
        return bytes;
    }

    private byte[] readFileWithLimit(File file) throws Exception {
        if (file.length() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("배경 제거 이미지가 2MB를 초과합니다.");
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private String downloadStorageImage(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty() || storagePath.startsWith("local://")) {
            return "";
        }
        try {
            File directory = new File(context.getFilesDir(), "closet_remote_cache");
            if (!directory.exists() && !directory.mkdirs()) return "";
            String extension = storagePath.toLowerCase().endsWith(".webp") ? ".webp"
                    : storagePath.toLowerCase().endsWith(".png") ? ".png" : ".jpg";
            File cached = new File(directory, Integer.toHexString(storagePath.hashCode()) + extension);
            if (cached.exists() && cached.length() > 0) return cached.getAbsolutePath();

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    ApiClient.STORAGE_URL + "/object/authenticated/" + ApiClient.SUPABASE_CLOTHING_BUCKET
                            + "/" + encodeStoragePath(storagePath)
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            applyAuthHeaders(connection);
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                readResponse(connection, statusCode);
                return "";
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(cached)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            return cached.getAbsolutePath();
        } catch (Exception error) {
            System.out.println("[ClothesBackendService] Private image download failed: " + error.getMessage());
            return "";
        }
    }
}
