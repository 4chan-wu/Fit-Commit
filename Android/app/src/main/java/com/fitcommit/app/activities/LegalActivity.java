package com.fitcommit.app.activities;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
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
        back.setText("‹  돌아가기");
        back.setTextColor(getColor(R.color.fit_primary));
        back.setTextSize(16);
        back.setPadding(0, 0, 0, padding);
        back.setOnClickListener(v -> finish());
        content.addView(back);

        TextView title = new TextView(this);
        title.setText("개인정보처리방침");
        title.setTextColor(getColor(R.color.fit_text));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.privacy_policy_body));
        body.setTextColor(getColor(R.color.fit_text));
        body.setTextSize(14);
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
