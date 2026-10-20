package com.fitcommit.app.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.ClosetItem;
import com.fitcommit.app.services.BackgroundRemover;
import com.fitcommit.app.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 실제 의류 사진을 중심으로 보여주는 2열 옷장 어댑터입니다.
 */
public class ClosetCardAdapter extends RecyclerView.Adapter<ClosetCardAdapter.ClothingViewHolder> {
    public interface OnItemActionListener {
        void onOpen(ClosetItem item);
        void onManage(ClosetItem item);
    }

    private final Context context;
    private final OnItemActionListener actionListener;
    private final List<ClosetItem> items = new ArrayList<>();

    public ClosetCardAdapter(Context context, OnItemActionListener actionListener) {
        this.context = context;
        this.actionListener = actionListener;
    }

    public void submitList(List<ClosetItem> newItems) {
        List<ClosetItem> oldItems = new ArrayList<>(items);
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return Objects.equals(
                        oldItems.get(oldPosition).localId,
                        newItems.get(newPosition).localId
                );
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                ClosetItem oldItem = oldItems.get(oldPosition);
                ClosetItem newItem = newItems.get(newPosition);
                return Objects.equals(oldItem.name, newItem.name)
                        && Objects.equals(oldItem.category, newItem.category)
                        && Objects.equals(oldItem.color, newItem.color)
                        && Objects.equals(oldItem.season, newItem.season)
                        && Objects.equals(oldItem.imagePath, newItem.imagePath)
                        && Objects.equals(oldItem.analysisStatus, newItem.analysisStatus)
                        && Objects.equals(oldItem.syncStatus, newItem.syncStatus);
            }
        });
        items.clear();
        items.addAll(newItems);
        changes.dispatchUpdatesTo(this);
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
        holder.meta.setText(context.getString(
                R.string.item_meta,
                categoryLabel(item.category),
                seasonLabel(item.season)
        ));

        int itemColor = UiUtils.parseColorOrDefault(item.color, Color.LTGRAY);
        GradientDrawable colorDot = new GradientDrawable();
        colorDot.setShape(GradientDrawable.OVAL);
        colorDot.setColor(itemColor);
        holder.color.setBackground(colorDot);

        File imageFile = item.imagePath == null ? null : new File(item.imagePath);
        if (imageFile != null && imageFile.exists()) {
            Bitmap bitmap = null;
            try {
                bitmap = BackgroundRemover.loadOrientedBitmap(imageFile.getAbsolutePath(), 720);
            } catch (Exception error) {
                System.out.println("[ClosetCardAdapter] Image decode failed: " + error.getMessage());
            }
            if (bitmap != null) {
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
                bindPlaceholder(holder, itemColor);
            }
        } else {
            bindPlaceholder(holder, itemColor);
        }

        boolean analysisFailed = "failed".equals(item.analysisStatus);
        boolean analysisPending = "pending".equals(item.analysisStatus);
        boolean syncFailed = "sync_failed".equals(item.syncStatus);
        if (analysisFailed || analysisPending || syncFailed) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText(syncFailed
                    ? R.string.item_sync_required
                    : analysisFailed ? R.string.item_review_required : R.string.item_saving);
        } else {
            holder.status.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> actionListener.onOpen(item));
        holder.itemView.setOnLongClickListener(v -> {
            actionListener.onManage(item);
            return true;
        });
        holder.moreButton.setOnClickListener(v -> actionListener.onManage(item));
    }

    private int withSoftAlpha(int color) {
        return Color.argb(54, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void bindPlaceholder(ClothingViewHolder holder, int itemColor) {
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
        String[] labels = context.getResources().getStringArray(R.array.clothing_category_labels);
        if (value.contains("top") || value.contains("shirt") || value.contains("상의")) return labels[0];
        if (value.contains("bottom") || value.contains("pants") || value.contains("skirt") || value.contains("하의")) return labels[1];
        if (value.contains("outer") || value.contains("jacket") || value.contains("coat") || value.contains("아우터")) return labels[2];
        if (value.contains("shoe") || value.contains("신발")) return labels[3];
        if (value.contains("hat") || value.contains("모자")) return labels[4];
        if (value.contains("bag") || value.contains("가방")) return labels[5];
        if (value.contains("dress") || value.contains("원피스")) return labels[6];
        return category == null || category.trim().isEmpty() ? context.getString(R.string.category_other) : category;
    }

    private String seasonLabel(String season) {
        String value = season == null ? "" : season.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("all-season")
                || (value.contains("spring") && value.contains("summer")
                && value.contains("fall") && value.contains("winter"))) {
            return context.getString(R.string.season_all_year);
        }
        List<String> labels = new ArrayList<>();
        String[] seasonLabels = context.getResources().getStringArray(R.array.closet_season_filter_labels);
        if (value.contains("spring") || value.contains("봄")) labels.add(seasonLabels[1]);
        if (value.contains("summer") || value.contains("여름")) labels.add(seasonLabels[2]);
        if (value.contains("fall") || value.contains("autumn") || value.contains("가을")) labels.add(seasonLabels[3]);
        if (value.contains("winter") || value.contains("겨울")) labels.add(seasonLabels[4]);
        return labels.isEmpty() ? season : android.text.TextUtils.join("·", labels);
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
