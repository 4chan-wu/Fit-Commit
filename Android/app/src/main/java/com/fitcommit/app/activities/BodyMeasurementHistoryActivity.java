package com.fitcommit.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fitcommit.app.R;
import com.fitcommit.app.models.BodyMeasurement;
import com.fitcommit.app.services.BodyMeasurementService;
import com.fitcommit.app.services.BodyMeasurementStorage;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

public final class BodyMeasurementHistoryActivity extends Activity {
    private LinearLayout historyList;
    private TextView emptyText;
    private TextView syncStatus;
    private View summaryCard;
    private TextView latestShape;
    private TextView latestMetrics;
    private TextView latestChange;
    private BodyMeasurementService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_measurement_history);
        service = new BodyMeasurementService(this);
        bindViews();
        applyInsets();
        findViewById(R.id.bodyHistoryBackButton).setOnClickListener(view -> finish());

        List<BodyMeasurement> local = BodyMeasurementStorage.all(this);
        render(local);
        if (!service.isConfigured()) {
            syncStatus.setText(R.string.body_history_local_only);
            return;
        }
        syncStatus.setText(R.string.body_history_loading);
        new Thread(() -> loadServerHistory(local)).start();
    }

    private void bindViews() {
        historyList = findViewById(R.id.bodyHistoryList);
        emptyText = findViewById(R.id.bodyHistoryEmptyText);
        syncStatus = findViewById(R.id.bodyHistorySyncStatus);
        summaryCard = findViewById(R.id.bodyHistorySummaryCard);
        latestShape = findViewById(R.id.bodyHistoryLatestShape);
        latestMetrics = findViewById(R.id.bodyHistoryLatestMetrics);
        latestChange = findViewById(R.id.bodyHistoryLatestChange);
    }

    private void applyInsets() {
        View root = findViewById(R.id.bodyHistoryRoot);
        int baseBottom = dp(32);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom + baseBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void loadServerHistory(List<BodyMeasurement> local) {
        try {
            List<BodyMeasurement> merged = service.fetchAndSyncLocal(local);
            BodyMeasurementStorage.replaceAll(this, merged);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                render(merged);
                syncStatus.setText(getResources().getQuantityString(
                        R.plurals.body_history_synced_count,
                        merged.size(),
                        merged.size()
                ));
            });
        } catch (Exception error) {
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    syncStatus.setText(R.string.body_history_sync_failed);
                }
            });
        }
    }

    private void render(List<BodyMeasurement> measurements) {
        historyList.removeAllViews();
        boolean empty = measurements == null || measurements.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        summaryCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        BodyMeasurement newest = measurements.get(0);
        latestShape.setText(shapeLabel(newest.bodyShape));
        latestMetrics.setText(getString(
                R.string.body_history_latest_metrics,
                newest.heightMeters * 100f,
                newest.shoulderWidthMeters * 100f,
                newest.hipWidthMeters * 100f
        ));
        if (measurements.size() > 1) {
            latestChange.setText(changeText(newest, measurements.get(1)));
            latestChange.setVisibility(View.VISIBLE);
        } else {
            latestChange.setText(R.string.body_history_first_record);
            latestChange.setVisibility(View.VISIBLE);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int index = 0; index < measurements.size(); index++) {
            BodyMeasurement measurement = measurements.get(index);
            View item = inflater.inflate(R.layout.item_body_measurement_history, historyList, false);
            setText(item, R.id.bodyHistoryItemDate, DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    Locale.getDefault()
            ).format(measurement.measuredAt));
            setText(item, R.id.bodyHistoryItemMethod, methodLabel(measurement.measurementMethod));
            setText(item, R.id.bodyHistoryItemShape, shapeLabel(measurement.bodyShape));
            setText(item, R.id.bodyHistoryItemMetrics, getString(
                    R.string.body_history_item_metrics,
                    measurement.heightMeters * 100f,
                    measurement.shoulderWidthMeters * 100f,
                    measurement.hipWidthMeters * 100f,
                    measurement.armLengthMeters * 100f,
                    measurement.legLengthMeters * 100f
            ));
            TextView change = item.findViewById(R.id.bodyHistoryItemChange);
            if (index + 1 < measurements.size()) {
                change.setText(changeText(measurement, measurements.get(index + 1)));
                change.setVisibility(View.VISIBLE);
            } else {
                change.setVisibility(View.GONE);
            }
            historyList.addView(item);
        }
    }

    private String changeText(BodyMeasurement current, BodyMeasurement previous) {
        return getString(
                R.string.body_history_change,
                signedCentimeters(current.shoulderWidthMeters - previous.shoulderWidthMeters),
                signedCentimeters(current.hipWidthMeters - previous.hipWidthMeters)
        );
    }

    private String signedCentimeters(float meters) {
        return String.format(Locale.getDefault(), "%+.1f cm", meters * 100f);
    }

    private String methodLabel(String method) {
        return getString("manual_height".equals(method)
                ? R.string.body_history_method_manual
                : R.string.body_history_method_ar);
    }

    private String shapeLabel(String value) {
        if ("inverted_triangle".equals(value) || "역삼각형".equals(value)) {
            return getString(R.string.body_shape_inverted_triangle);
        }
        if ("triangle".equals(value) || "삼각형".equals(value)) {
            return getString(R.string.body_shape_triangle);
        }
        if ("rectangle".equals(value) || "직사각형/스탠다드".equals(value)) {
            return getString(R.string.body_shape_rectangle);
        }
        return getString(R.string.body_shape_unknown);
    }

    private void setText(View root, int id, String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
