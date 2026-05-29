package com.fitcommit.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fitcommit.app.R;
import com.fitcommit.app.adapters.ChatMessageAdapter;
import com.fitcommit.app.models.ChatMessage;
import com.fitcommit.app.services.ChatService;
import com.fitcommit.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS AIChatView에 대응하는 패트리 채팅 화면입니다.
 * 첨부/전송 UI를 유지하되 실제 파일 업로드는 추후 API 연결 지점으로 남겨둡니다.
 */
public class AIChatActivity extends Activity {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final ChatService chatService = new ChatService();
    private ChatMessageAdapter adapter;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText messageEditText;
    private LinearLayout chatInputContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        adapter = new ChatMessageAdapter(this);
        messageContainer = findViewById(R.id.messageContainer);
        chatScroll = findViewById(R.id.chatScroll);
        messageEditText = findViewById(R.id.messageEditText);
        chatInputContainer = findViewById(R.id.chatInputContainer);

        applyNavigationBarInsetPadding();

        findViewById(R.id.backText).setOnClickListener(v -> finish());
        findViewById(R.id.attachButton).setOnClickListener(v -> UiUtils.toast(this, "사진/파일 첨부는 추후 연결됩니다."));
        findViewById(R.id.sendButton).setOnClickListener(v -> sendMessage());

        messages.add(new ChatMessage("안녕하세요! 저는 패션 전문 AI 패트리에요. 오늘 어떤 스타일을 완성해볼까요?", false));
        renderMessages();
    }

    /**
     * 사용자 메시지를 추가하고 ChatService를 통해 더미 AI 응답을 이어 붙입니다.
     */
    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        messageEditText.setText("");
        messages.add(new ChatMessage(text, true));
        messages.add(new ChatMessage(chatService.askPatri(text), false));
        renderMessages();
    }

    private void renderMessages() {
        adapter.bind(messageContainer, messages);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void applyNavigationBarInsetPadding() {
        final int baseBottomPadding = chatInputContainer.getPaddingBottom();
        final int extraPadding = dpToPx(8);

        ViewCompat.setOnApplyWindowInsetsListener(chatInputContainer, (view, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    baseBottomPadding + bottomInset + extraPadding
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(chatInputContainer);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
