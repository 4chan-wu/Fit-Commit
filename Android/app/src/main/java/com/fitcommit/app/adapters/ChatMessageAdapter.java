package com.fitcommit.app.adapters;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fitcommit.app.R;
import com.fitcommit.app.models.ChatMessage;
import com.fitcommit.app.utils.UiUtils;

import java.util.List;

/**
 * AIChatView의 말풍선 UI를 LinearLayout에 렌더링하는 간단한 어댑터입니다.
 */
public class ChatMessageAdapter {
    private final Context context;

    public ChatMessageAdapter(Context context) {
        this.context = context;
    }

    public void bind(LinearLayout container, List<ChatMessage> messages) {
        container.removeAllViews();
        for (ChatMessage message : messages) {
            container.addView(createBubble(message));
        }
    }

    private TextView createBubble(ChatMessage message) {
        TextView bubble = new TextView(context);
        bubble.setText(message.content);
        bubble.setTextSize(15);
        bubble.setLineSpacing(UiUtils.dp(context, 3), 1.0f);
        bubble.setPadding(UiUtils.dp(context, 16), UiUtils.dp(context, 12), UiUtils.dp(context, 16), UiUtils.dp(context, 12));
        bubble.setBackgroundResource(message.fromUser ? R.drawable.bg_chat_user : R.drawable.bg_chat_ai);
        bubble.setTextColor(message.fromUser ? 0xFFFFFFFF : context.getColor(R.color.fit_text));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.width = UiUtils.dp(context, 290);
        params.setMargins(0, 0, 0, UiUtils.dp(context, 12));
        params.gravity = message.fromUser ? Gravity.END : Gravity.START;
        bubble.setLayoutParams(params);

        return bubble;
    }
}
