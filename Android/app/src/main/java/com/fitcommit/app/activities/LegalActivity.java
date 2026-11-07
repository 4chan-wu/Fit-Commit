package com.fitcommit.app.activities;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fitcommit.app.R;

public class LegalActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView back = new TextView(this);
        back.setText(R.string.legal_back);
        back.setTextColor(getColor(R.color.fit_primary));
        back.setTextSize(16);
        back.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        back.setPadding(0, 0, 0, padding);
        back.setOnClickListener(v -> finish());
        content.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.privacy_policy_title);
        title.setTextColor(getColor(R.color.fit_text));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.privacy_policy_body));
        body.setTextColor(getColor(R.color.fit_text));
        body.setTextSize(14);
        // 개인정보처리방침 본문은 긴 문단이라 읽기 편하도록 왼쪽 정렬을 유지합니다.
        body.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        body.setLineSpacing(0, 1.45f);
        body.setPadding(0, padding, 0, padding);
        body.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        content.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getColor(R.color.fit_bg));
        scrollView.addView(content);
        setContentView(scrollView);
    }
}
