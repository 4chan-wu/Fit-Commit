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

/**
 * 실제 의류 사진을 중심으로 보여주는 2열 옷장 어댑터입니다.
 */
public class ClosetCardAdapter extends RecyclerView.Adapter<ClosetCardAdapter.ClothingViewHolder> {
    private final Context context;
    private final List<ClosetItem> items = new ArrayList<>();

    public ClosetCardAdapter(Context context) {
        this.context = context;
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
        holder.name.setText(item.name);
        holder.meta.setText(item.category + " · " + item.season);

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
    }

    private int withSoftAlpha(int color) {
        return Color.argb(54, Color.red(color), Color.green(color), Color.blue(color));
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
        final View color;

        ClothingViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.closetItemImage);
            status = itemView.findViewById(R.id.closetItemStatus);
            name = itemView.findViewById(R.id.closetItemName);
            meta = itemView.findViewById(R.id.closetItemMeta);
            color = itemView.findViewById(R.id.closetItemColor);
        }
    }
}
