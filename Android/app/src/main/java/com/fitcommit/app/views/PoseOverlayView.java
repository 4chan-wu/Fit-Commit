package com.fitcommit.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.Collections;
import java.util.List;

public class PoseOverlayView extends View {
    private static final int[][] CONNECTIONS = {
            {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
            {11, 23}, {12, 24}, {23, 24}, {23, 25}, {25, 27},
            {24, 26}, {26, 28}, {27, 29}, {29, 31}, {28, 30}, {30, 32}
    };

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<NormalizedLandmark> landmarks = Collections.emptyList();
    private int imageWidth;
    private int imageHeight;

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setColor(Color.rgb(99, 132, 255));
        linePaint.setStrokeWidth(dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        pointPaint.setColor(Color.WHITE);
        pointPaint.setStyle(Paint.Style.FILL);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setResult(PoseLandmarkerResult result, int sourceWidth, int sourceHeight) {
        if (result == null || result.landmarks().isEmpty()) {
            clear();
            return;
        }
        landmarks = result.landmarks().get(0);
        imageWidth = sourceWidth;
        imageHeight = sourceHeight;
        invalidate();
    }

    public void clear() {
        landmarks = Collections.emptyList();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (landmarks.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return;

        float scale = Math.max(getWidth() / (float) imageWidth, getHeight() / (float) imageHeight);
        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        linePaint.setShadowLayer(dp(8), 0, 0, Color.rgb(49, 94, 255));
        for (int[] connection : CONNECTIONS) {
            NormalizedLandmark start = landmarks.get(connection[0]);
            NormalizedLandmark end = landmarks.get(connection[1]);
            if (visibility(start) < 0.5f || visibility(end) < 0.5f) continue;
            canvas.drawLine(
                    start.x() * imageWidth * scale + offsetX,
                    start.y() * imageHeight * scale + offsetY,
                    end.x() * imageWidth * scale + offsetX,
                    end.y() * imageHeight * scale + offsetY,
                    linePaint
            );
        }

        pointPaint.setShadowLayer(dp(8), 0, 0, Color.rgb(49, 94, 255));
        int[] keyPoints = {0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28};
        for (int index : keyPoints) {
            NormalizedLandmark point = landmarks.get(index);
            if (visibility(point) < 0.5f) continue;
            canvas.drawCircle(
                    point.x() * imageWidth * scale + offsetX,
                    point.y() * imageHeight * scale + offsetY,
                    dp(5),
                    pointPaint
            );
        }
    }

    private float visibility(NormalizedLandmark landmark) {
        return landmark.visibility().orElse(0f);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
