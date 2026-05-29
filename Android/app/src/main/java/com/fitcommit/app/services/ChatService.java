package com.fitcommit.app.services;

/**
 * AI 채팅 응답 생성 서비스입니다.
 * 현재는 로컬 더미 응답이며, 추후 Backend /chat API와 연결합니다.
 */
public class ChatService {
    public String askPatri(String message) {
        // TODO: Backend POST /chat/rooms/{room_id}/messages 연동
        if (message == null || message.trim().isEmpty()) {
            return "궁금한 스타일을 편하게 입력해주세요.";
        }
        return "좋은 질문이에요. 지금 분위기에는 톤을 하나로 묶고 작은 포인트만 남기는 스타일이 가장 좋아 보여요.";
    }
}
