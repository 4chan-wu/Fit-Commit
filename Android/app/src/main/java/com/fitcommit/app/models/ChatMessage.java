package com.fitcommit.app.models;

/**
 * AIChatView의 Message 구조를 Android 채팅 화면에서 사용하기 위한 모델입니다.
 */
public class ChatMessage {
    public final String content;
    public final boolean fromUser;

    public ChatMessage(String content, boolean fromUser) {
        this.content = content;
        this.fromUser = fromUser;
    }
}
