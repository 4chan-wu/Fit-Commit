package com.fitcommit.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.activities.AnalysisResultActivity;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.List;

/**
 * RecyclerView 없이도 XML 기반 GridLayout에 의류 카드를 채우기 위한 간단한 어댑터입니다.
 * 추후 아이템 수가 많아지면 RecyclerView.Adapter로 교체할 수 있습니다.
 */
public class ClosetCardAdapter {
    private final Context context;

    public ClosetCardAdapter(Context context) {
        this.context = context;
    }

    public void bind(GridLayout gridLayout, List<ClosetItem> items) {
        gridLayout.removeAllViews();
        for (ClosetItem item : items) {
            gridLayout.addView(createCard(item));
        }
    }

    private LinearLayout createCard(ClosetItem item) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(UiUtils.dp(context, 14), UiUtils.dp(context, 14), UiUtils.dp(context, 14), UiUtils.dp(context, 14));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = UiUtils.dp(context, 168);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(UiUtils.dp(context, 5), UiUtils.dp(context, 5), UiUtils.dp(context, 5), UiUtils.dp(context, 5));
        card.setLayoutParams(params);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(context, AnalysisResultActivity.class);
            intent.putExtra(AnalysisResultActivity.EXTRA_EDIT_MODE, true);
            intent.putExtra(AnalysisResultActivity.EXTRA_LOCAL_ID, item.localId);
            intent.putExtra(AnalysisResultActivity.EXTRA_REMOTE_ID, item.remoteId);
            intent.putExtra(AnalysisResultActivity.EXTRA_WARDROBE_ID, item.wardrobeId);
            intent.putExtra(AnalysisResultActivity.EXTRA_IMAGE_PATH, item.imagePath);
            intent.putExtra(AnalysisResultActivity.EXTRA_CATEGORY_HINT, item.category);
            intent.putExtra(AnalysisResultActivity.EXTRA_NAME, item.name);
            intent.putExtra(AnalysisResultActivity.EXTRA_COLOR, item.color);
            intent.putExtra(AnalysisResultActivity.EXTRA_SEASON, item.season);
            intent.putExtra(AnalysisResultActivity.EXTRA_NOTE, item.note);
            context.startActivity(intent);
        });

        if (item.imagePath != null && !item.imagePath.isEmpty() && new File(item.imagePath).exists()) {
            ImageView image = new ImageView(context);
            Bitmap bitmap = BitmapFactory.decodeFile(item.imagePath);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(context, 70)));
        } else {
            TextView swatch = new TextView(context);
            swatch.setText(" ");
            swatch.setBackgroundColor(UiUtils.parseColorOrDefault(item.color, Color.LTGRAY));
            card.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(context, 70)));
        }

        TextView name = new TextView(context);
        name.setText(item.name);
        name.setTextColor(context.getColor(R.color.fit_text));
        name.setTextSize(16);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = UiUtils.dp(context, 12);
        card.addView(name, nameParams);

        TextView meta = new TextView(context);
        meta.setText(item.category + " · " + item.season);
        meta.setTextColor(context.getColor(R.color.fit_text_secondary));
        meta.setTextSize(12);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = UiUtils.dp(context, 5);
        card.addView(meta, metaParams);

        if ("pending".equals(item.analysisStatus) || "failed".equals(item.analysisStatus)) {
            TextView status = new TextView(context);
            status.setText("failed".equals(item.analysisStatus) ? "분석 실패 · 다시 분석 가능" : "AI 분석 대기");
            status.setTextColor(context.getColor(R.color.fit_accent));
            status.setTextSize(11);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            statusParams.topMargin = UiUtils.dp(context, 4);
            card.addView(status, statusParams);
        }

        if ("sync_failed".equals(item.syncStatus)) {
            TextView sync = new TextView(context);
            sync.setText("서버 동기화 실패");
            sync.setTextColor(Color.RED);
            sync.setTextSize(11);
            LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            syncParams.topMargin = UiUtils.dp(context, 4);
            card.addView(sync, syncParams);
        }

        return card;
    }
}
