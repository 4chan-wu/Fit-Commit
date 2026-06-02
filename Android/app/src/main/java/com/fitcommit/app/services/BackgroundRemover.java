package com.fitcommit.app.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class BackgroundRemover {
    private BackgroundRemover() {
    }

    public static String processUri(Context context, Uri imageUri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            return processBitmap(context, bitmap);
        }
    }

    public static String saveOriginalUri(Context context, Uri imageUri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            return saveOriginalBitmap(context, bitmap);
        }
    }

    public static String saveOriginalBitmap(Context context, Bitmap source) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }

        Bitmap output = scaleDown(source, 1200);
        File dir = new File(context.getFilesDir(), "closet_images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("이미지 저장 폴더를 만들 수 없습니다.");
        }

        File file = new File(dir, "clothing_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            output.compress(Bitmap.CompressFormat.JPEG, 88, outputStream);
        }
        return file.getAbsolutePath();
    }

    public static String processBitmap(Context context, Bitmap source) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }

        Bitmap scaled = scaleDown(source, 1200);
        Bitmap output = removeBrightBackground(scaled);
        File dir = new File(context.getFilesDir(), "closet_images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("이미지 저장 폴더를 만들 수 없습니다.");
        }

        File file = new File(dir, "clothing_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            output.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        }
        return file.getAbsolutePath();
    }

    private static Bitmap scaleDown(Bitmap source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        int max = Math.max(width, height);
        if (max <= maxSize) {
            return source.copy(Bitmap.Config.ARGB_8888, true);
        }

        float ratio = maxSize / (float) max;
        return Bitmap.createScaledBitmap(source, Math.round(width * ratio), Math.round(height * ratio), true);
    }

    private static Bitmap removeBrightBackground(Bitmap source) {
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                int pixel = output.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                if (red > 238 && green > 238 && blue > 238) {
                    output.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return output;
    }
}
