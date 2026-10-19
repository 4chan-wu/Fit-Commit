package com.fitcommit.app.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class BackgroundRemover {
    public interface Callback {
        void onSuccess(String processedImagePath);

        void onError(String message);
    }

    private BackgroundRemover() {
    }

    public static String saveOriginalUri(Context context, Uri imageUri) throws Exception {
        return saveOriginalBitmap(context, decodeUri(context, imageUri, 2000));
    }

    public static String saveOriginalBitmap(Context context, Bitmap source) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }

        Bitmap output = scaleDown(source, 2000);
        File dir = new File(context.getFilesDir(), "closet_originals");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("이미지 저장 폴더를 만들 수 없습니다.");
        }

        File file = new File(dir, "clothing_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            if (!output.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)) {
                throw new IllegalStateException("원본 이미지를 저장하지 못했습니다.");
            }
        } finally {
            output.recycle();
            source.recycle();
        }
        return file.getAbsolutePath();
    }

    public static String normalizeCapturedFile(Context context, String imagePath) throws Exception {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("촬영 이미지 경로가 없습니다.");
        }
        File originalFile = new File(imagePath);
        if (!originalFile.exists()) {
            throw new IllegalArgumentException("촬영 이미지를 찾을 수 없습니다.");
        }
        String normalizedPath = saveOriginalBitmap(context, loadOrientedBitmap(imagePath, 2000));
        if (!originalFile.getAbsolutePath().equals(normalizedPath) && !originalFile.delete()) {
            System.out.println("[BackgroundRemover] High-resolution original cleanup failed: " + imagePath);
        }
        return normalizedPath;
    }

    public static Bitmap loadOrientedBitmap(String imagePath, int maxSize) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(imagePath, options);
        if (decoded == null) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }

        Bitmap oriented = applyExifOrientation(decoded, imagePath);
        if (oriented != decoded) decoded.recycle();
        Bitmap scaled = scaleToMax(oriented, maxSize);
        if (scaled != oriented) oriented.recycle();
        return scaled;
    }

    public static void processFile(Context context, String originalImagePath, Callback callback) {
        Bitmap decoded;
        try {
            decoded = loadOrientedBitmap(originalImagePath, 1600);
            if (decoded == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        } catch (Exception error) {
            callback.onError(safeMessage(error));
            return;
        }

        Bitmap inputBitmap = scaleDown(decoded, 1600);
        if (decoded != inputBitmap) decoded.recycle();
        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);
        segmenter.process(InputImage.fromBitmap(inputBitmap, 0))
                .addOnSuccessListener(result -> {
                    Bitmap foreground = result.getForegroundBitmap();
                    try {
                        if (foreground == null || !hasUsefulForeground(foreground)) {
                            throw new IllegalStateException("옷을 분리하지 못했습니다. 단색 배경에서 다시 촬영해주세요.");
                        }
                        callback.onSuccess(saveTransparentWebp(context, foreground));
                    } catch (Exception error) {
                        callback.onError(safeMessage(error));
                    } finally {
                        if (foreground != null) foreground.recycle();
                        inputBitmap.recycle();
                        segmenter.close();
                    }
                })
                .addOnFailureListener(error -> {
                    inputBitmap.recycle();
                    segmenter.close();
                    callback.onError("배경 제거 모델을 준비하지 못했습니다. 인터넷 연결 후 다시 시도해주세요. "
                            + safeMessage(error));
                });
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

    private static Bitmap scaleToMax(Bitmap source, int maxSize) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= maxSize) return source;
        float ratio = maxSize / (float) longest;
        return Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(source.getWidth() * ratio)),
                Math.max(1, Math.round(source.getHeight() * ratio)),
                true
        );
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, String imagePath) {
        int orientation;
        try {
            orientation = new ExifInterface(imagePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (Exception ignored) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap decodeUri(Context context, Uri uri, int maxSize) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        setDecoderTargetSize(decoder, info, maxSize);
                    }
            );
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(inputStream, null, bounds);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream, null, options);
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static void setDecoderTargetSize(
            ImageDecoder decoder,
            ImageDecoder.ImageInfo info,
            int maxSize
    ) {
        int width = info.getSize().getWidth();
        int height = info.getSize().getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxSize) return;
        float ratio = maxSize / (float) longest;
        decoder.setTargetSize(
                Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio))
        );
    }

    private static int sampleSize(int width, int height, int maxSize) {
        int sample = 1;
        while (Math.max(width / sample, height / sample) > maxSize * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static boolean hasUsefulForeground(Bitmap bitmap) {
        int step = Math.max(1, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 160);
        int foreground = 0;
        int sampled = 0;
        for (int y = 0; y < bitmap.getHeight(); y += step) {
            for (int x = 0; x < bitmap.getWidth(); x += step) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 24) foreground++;
                sampled++;
            }
        }
        float coverage = foreground / (float) Math.max(sampled, 1);
        return coverage >= 0.015f && coverage <= 0.98f;
    }

    private static String saveTransparentWebp(Context context, Bitmap foreground) throws Exception {
        File directory = new File(context.getFilesDir(), "closet_cutouts");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("배경 제거 이미지 폴더를 만들 수 없습니다.");
        }
        File file = new File(directory, "cutout_" + System.currentTimeMillis() + ".webp");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;
        if (!foreground.compress(format, 90, encoded)) {
            throw new IllegalStateException("투명 이미지를 저장하지 못했습니다.");
        }
        byte[] bytes = encoded.toByteArray();
        if (bytes.length > 2 * 1024 * 1024) {
            throw new IllegalStateException("배경 제거 이미지가 2MB를 초과합니다.");
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return file.getAbsolutePath();
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
    }
}
