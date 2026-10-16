package com.fitcommit.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fitcommit.app.R;
import com.fitcommit.app.activities.AnalysisResultActivity;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 실제 의류 사진을 중심으로 보여주는 2열 옷장 어댑터입니다.
 */
public class ClosetCardAdapter extends RecyclerView.Adapter<ClosetCardAdapter.ClothingViewHolder> {
    public interface OnDeleteClickListener {
        void onDeleteClick(ClosetItem item);
    }

    private final Context context;
    private final OnDeleteClickListener deleteClickListener;
    private final List<ClosetItem> items = new ArrayList<>();

    public ClosetCardAdapter(Context context, OnDeleteClickListener deleteClickListener) {
        this.context = context;
        this.deleteClickListener = deleteClickListener;
    }

    public void submitList(List<ClosetItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClothingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_closet_card, parent, false);
        return new ClothingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClothingViewHolder holder, int position) {
        ClosetItem item = items.get(position);
        holder.name.setText(displayName(item));
        holder.meta.setText(categoryLabel(item.category) + " · " + seasonLabel(item.season));

        int itemColor = UiUtils.parseColorOrDefault(item.color, Color.LTGRAY);
        GradientDrawable colorDot = new GradientDrawable();
        colorDot.setShape(GradientDrawable.OVAL);
        colorDot.setColor(itemColor);
        holder.color.setBackground(colorDot);

        File imageFile = item.imagePath == null ? null : new File(item.imagePath);
        if (imageFile != null && imageFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            holder.image.clearColorFilter();
            holder.image.setPadding(
                    UiUtils.dp(context, 10),
                    UiUtils.dp(context, 10),
                    UiUtils.dp(context, 10),
                    UiUtils.dp(context, 10)
            );
            holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.image.setImageBitmap(bitmap);
            holder.image.setBackgroundResource(R.drawable.bg_image_tile);
        } else {
            holder.image.setColorFilter(context.getColor(R.color.fit_text_secondary));
            holder.image.setPadding(
                    UiUtils.dp(context, 38),
                    UiUtils.dp(context, 38),
                    UiUtils.dp(context, 38),
                    UiUtils.dp(context, 38)
            );
            holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.image.setImageResource(R.drawable.ic_garment);
            GradientDrawable placeholder = new GradientDrawable();
            placeholder.setColor(withSoftAlpha(itemColor));
            placeholder.setCornerRadius(UiUtils.dp(context, 14));
            holder.image.setBackground(placeholder);
        }

        boolean analysisFailed = "failed".equals(item.analysisStatus);
        boolean analysisPending = "pending".equals(item.analysisStatus);
        boolean syncFailed = "sync_failed".equals(item.syncStatus);
        if (analysisFailed || analysisPending || syncFailed) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText(syncFailed ? "동기화 필요" : analysisFailed ? "정보 확인 필요" : "저장 중");
        } else {
            holder.status.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> openItem(item));
        holder.itemView.setOnLongClickListener(v -> {
            deleteClickListener.onDeleteClick(item);
            return true;
        });
        holder.moreButton.setOnClickListener(v -> deleteClickListener.onDeleteClick(item));
    }

    private int withSoftAlpha(int color) {
        return Color.argb(54, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String displayName(ClosetItem item) {
        String name = item.name == null ? "" : item.name.trim();
        String normalized = name.toLowerCase(Locale.ROOT);
        if (name.isEmpty() || normalized.equals("top") || normalized.equals("bottom")
                || normalized.equals("outer") || normalized.equals("shoes")
                || normalized.equals("hat") || normalized.equals("bag")
                || normalized.equals("dress")) {
            return categoryLabel(item.category);
        }
        return name;
    }

    private String categoryLabel(String category) {
        String value = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (value.contains("top") || value.contains("shirt") || value.contains("상의")) return "상의";
        if (value.contains("bottom") || value.contains("pants") || value.contains("skirt") || value.contains("하의")) return "하의";
        if (value.contains("outer") || value.contains("jacket") || value.contains("coat") || value.contains("아우터")) return "아우터";
        if (value.contains("shoe") || value.contains("신발")) return "신발";
        if (value.contains("hat") || value.contains("모자")) return "모자";
        if (value.contains("bag") || value.contains("가방")) return "가방";
        if (value.contains("dress") || value.contains("원피스")) return "원피스";
        return category == null || category.trim().isEmpty() ? "기타" : category;
    }

    private String seasonLabel(String season) {
        String value = season == null ? "" : season.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("all-season")
                || (value.contains("spring") && value.contains("summer")
                && value.contains("fall") && value.contains("winter"))) {
            return "사계절";
        }
        List<String> labels = new ArrayList<>();
        if (value.contains("spring") || value.contains("봄")) labels.add("봄");
        if (value.contains("summer") || value.contains("여름")) labels.add("여름");
        if (value.contains("fall") || value.contains("autumn") || value.contains("가을")) labels.add("가을");
        if (value.contains("winter") || value.contains("겨울")) labels.add("겨울");
        return labels.isEmpty() ? season : android.text.TextUtils.join("·", labels);
    }

    private void openItem(ClosetItem item) {
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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ClothingViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView status;
        final TextView name;
        final TextView meta;
        final TextView moreButton;
        final View color;

        ClothingViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.closetItemImage);
            status = itemView.findViewById(R.id.closetItemStatus);
            name = itemView.findViewById(R.id.closetItemName);
            meta = itemView.findViewById(R.id.closetItemMeta);
            moreButton = itemView.findViewById(R.id.closetItemMoreButton);
            color = itemView.findViewById(R.id.closetItemColor);
        }
    }
}
