package com.fitcommit.app.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.fitcommit.app.R;

/** Keeps form and reading screens comfortable on tablets and wide foldables. */
public final class ResponsiveContentLayout extends LinearLayout {
    public ResponsiveContentLayout(Context context) {
        super(context);
    }

    public ResponsiveContentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResponsiveContentLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maximumWidth = getResources().getDimensionPixelSize(R.dimen.fit_content_max_width);
        int requestedWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (requestedWidth > maximumWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maximumWidth, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
