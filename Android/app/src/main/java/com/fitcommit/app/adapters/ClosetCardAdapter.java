package com.fitcommit.app.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.utils.UiUtils;

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

        TextView swatch = new TextView(context);
        swatch.setText(" ");
        swatch.setBackgroundColor(UiUtils.parseColorOrDefault(item.color, Color.LTGRAY));
        card.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(context, 70)));

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

        return card;
    }
}
