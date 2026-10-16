import json
from uuid import UUID

from sqlalchemy import text
from sqlalchemy.orm import Session

MODEL_NAME = "gpt-4o-mini"


def make_chat_title(content: str) -> str:
    title = (content or "").strip()

    if len(title) > 20:
        title = title[:20] + "..."

    return title or "새 채팅"


def get_recent_messages(db: Session, room_id: int, limit: int = 10):
    rows = db.execute(
        text("""
            SELECT id, sender_type, content
            FROM chat_messages
            WHERE chat_room_id = :room_id
            ORDER BY id DESC
            LIMIT :limit
        """),
        {"room_id": room_id, "limit": limit},
    ).mappings().all()

    return list(reversed(rows))


def build_messages_for_ai(recent_messages: list[dict]) -> list[dict]:
    messages = [
        {
            "role": "system",
            "content": (
                "너는 패션 스타일과 코디를 도와주는 친절한 AI 어시스턴트야. "
                "답변은 짧고 이해하기 쉽게 해줘. "
                "가능하면 상의, 하의, 신발처럼 구체적인 아이템 중심으로 추천해줘."
            ),
        }
    ]

    for msg in recent_messages:
        if msg["sender_type"] == "user":
            role = "user"
        elif msg["sender_type"] == "assistant":
            role = "assistant"
        else:
            continue

        messages.append({
            "role": role,
            "content": msg["content"],
        })

    return messages


def save_ai_message(db: Session, room_id: int, ai_answer: str):
    db.execute(
        text("""
            INSERT INTO chat_messages (
                chat_room_id, sender_type, message_type, content, model_name
            )
            VALUES (
                :chat_room_id, 'assistant', 'text', :content, :model_name
            )
        """),
        {
            "chat_room_id": room_id,
            "content": ai_answer,
            "model_name": MODEL_NAME,
        },
    )


def save_ai_log(
    db: Session,
    user_id: UUID,
    room_id: int,
    request_payload: dict,
    response_payload: dict,
    success: bool,
    error_message: str | None = None,
):
    db.execute(
        text("""
            INSERT INTO ai_request_logs (
                user_id, chat_room_id, request_type, model_name,
                request_payload, response_payload, success, error_message
            )
            VALUES (
                :user_id, :chat_room_id, 'chat', :model_name,
                CAST(:request_payload AS JSONB),
                CAST(:response_payload AS JSONB),
                :success, :error_message
            )
        """),
        {
            "user_id": user_id,
            "chat_room_id": room_id,
            "model_name": MODEL_NAME,
            "request_payload": json.dumps(request_payload, ensure_ascii=False),
            "response_payload": json.dumps(response_payload, ensure_ascii=False),
            "success": success,
            "error_message": error_message,
        },
    )
