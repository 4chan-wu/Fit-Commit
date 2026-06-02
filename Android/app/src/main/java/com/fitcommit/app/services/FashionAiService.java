package com.fitcommit.app.services;

import com.fitcommit.app.models.AnalysisResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AI 서버 /predict multipart 호출 서비스입니다.
 */
public class FashionAiService {
    private static final String PREDICT_URL = "http://10.0.2.2:8000/predict";

    public AnalysisResult analyze(File imageFile, String categoryHint) throws Exception {
        String boundary = "Boundary-" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(PREDICT_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(35_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            writeTextPart(output, boundary, "category_hint", categoryHint);
            writeFilePart(output, boundary, "file", imageFile);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        int statusCode = connection.getResponseCode();
        String responseBody = readResponse(connection, statusCode);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(responseBody);
        }

        return parseResult(responseBody, categoryHint);
    }

    private void writeTextPart(DataOutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(DataOutputStream output, String boundary, String name, File file) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        java.io.InputStream rawInput = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (rawInput == null) {
            return "";
        }

        try (BufferedInputStream input = new BufferedInputStream(rawInput)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private AnalysisResult parseResult(String responseBody, String categoryHint) throws Exception {
        JSONObject root = new JSONObject(responseBody);
        JSONObject analysis = root.optJSONObject("analysis");
        if (analysis == null) {
            analysis = root;
        }

        String mainCategory = firstNonEmpty(
                analysis.optString("main_category"),
                analysis.optString("category"),
                categoryHint
        );
        String mainColor = firstNonEmpty(
                analysis.optString("main_color"),
                analysis.optString("color"),
                "#CCCCCC"
        );
        double confidence = analysis.has("confidence")
                ? analysis.optDouble("confidence", 0)
                : analysis.optDouble("confidence_cat", 0);

        return new AnalysisResult(
                mainCategory,
                analysis.optString("subcategory"),
                mainColor,
                firstNonEmpty(analysis.optString("season"), "All-Season"),
                parseStyleTags(analysis.optJSONArray("style_tags")),
                confidence,
                firstNonEmpty(analysis.optString("image_url"), analysis.optString("image_ref"))
        );
    }

    private String[] parseStyleTags(JSONArray array) {
        if (array == null) {
            return new String[0];
        }
        String[] tags = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            tags[i] = array.optString(i);
        }
        return tags;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
