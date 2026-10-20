from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.chat import CreateChatRoomRequest, SendMessageRequest
from app.services.chat_service import (
    get_recent_messages,
    build_messages_for_ai,
    save_ai_message,
    save_ai_log,
    make_chat_title,
)
from app.services.openai_service import call_openai_chat
from app.services.supabase_auth_service import user_resolver

router = APIRouter(prefix="/chat", tags=["chat"])


@router.post("/rooms")
async def create_chat_room(
    body: CreateChatRoomRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    user_id = await user_resolver.resolve_user_id(request)

    result = db.execute(
        text("""
            INSERT INTO chat_rooms (user_id, title, room_type)
            VALUES (:user_id, :title, :room_type)
            RETURNING id, title, room_type
        """),
        {
            "user_id": user_id,
            "title": body.title,
            "room_type": body.room_type,
        },
    )
    db.commit()

    room = result.mappings().first()
    return room


@router.get("/rooms")
async def get_chat_rooms(request: Request, db: Session = Depends(get_db)):
    user_id = await user_resolver.resolve_user_id(request)

    rows = db.execute(
        text("""
            SELECT id, title, room_type, created_at, updated_at
            FROM chat_rooms
            WHERE user_id = :user_id
            ORDER BY updated_at DESC, id DESC
        """),
        {"user_id": user_id},
    ).mappings().all()

    return rows


@router.get("/rooms/{room_id}/messages")
async def get_chat_messages(room_id: int, request: Request, db: Session = Depends(get_db)):
    user_id = await user_resolver.resolve_user_id(request)

    room = db.execute(
        text("""
            SELECT id
            FROM chat_rooms
            WHERE id = :room_id AND user_id = :user_id
        """),
        {"room_id": room_id, "user_id": user_id},
    ).mappings().first()

    if not room:
        raise HTTPException(status_code=404, detail="채팅방을 찾을 수 없습니다.")

    rows = db.execute(
        text("""
            SELECT id, chat_room_id, sender_type, message_type, content, model_name, created_at
            FROM chat_messages
            WHERE chat_room_id = :room_id
            ORDER BY id ASC
        """),
        {"room_id": room_id},
    ).mappings().all()

    return rows


@router.post("/rooms/{room_id}/messages")
async def send_message(
    room_id: int,
    body: SendMessageRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    user_id = await user_resolver.resolve_user_id(request)

    content = body.content.strip()

    if not content:
        raise HTTPException(status_code=400, detail="메시지가 비어 있습니다.")

    if len(content) > 1000:
        raise HTTPException(status_code=400, detail="메시지는 1000자 이하로 입력해주세요.")

    room = db.execute(
        text("""
            SELECT id, title
            FROM chat_rooms
            WHERE id = :room_id AND user_id = :user_id
        """),
        {"room_id": room_id, "user_id": user_id},
    ).mappings().first()

    if not room:
        raise HTTPException(status_code=404, detail="채팅방을 찾을 수 없습니다.")

    db.execute(
        text("""
            INSERT INTO chat_messages (
                chat_room_id, sender_type, message_type, content
            )
            VALUES (
                :chat_room_id, 'user', 'text', :content
            )
        """),
        {
            "chat_room_id": room_id,
            "content": content,
        },
    )

    if not room["title"]:
        db.execute(
            text("""
                UPDATE chat_rooms
                SET title = :title, updated_at = NOW()
                WHERE id = :room_id
            """),
            {
                "title": make_chat_title(content),
                "room_id": room_id,
            },
        )

    db.commit()

    recent_messages = get_recent_messages(db, room_id)
    ai_messages = build_messages_for_ai(recent_messages)

    try:
        ai_answer = call_openai_chat(ai_messages)

        save_ai_message(db, room_id, ai_answer)

        save_ai_log(
            db,
            user_id,
            room_id,
            request_payload={"messages": ai_messages},
            response_payload={"answer": ai_answer},
            success=True,
        )

        db.execute(
            text("""
                UPDATE chat_rooms
                SET updated_at = NOW()
                WHERE id = :room_id
            """),
            {"room_id": room_id},
        )

        db.commit()

        return {
            "user_message": content,
            "ai_message": ai_answer,
            "error": False
        }

    except Exception as e:
        fallback_answer = "현재 AI 응답 생성에 문제가 있습니다. 잠시 후 다시 시도해주세요."

        save_ai_message(db, room_id, fallback_answer)

        save_ai_log(
            db,
            user_id,
            room_id,
            request_payload={"messages": ai_messages},
            response_payload={},
            success=False,
            error_message=str(e),
        )

        db.commit()

        return {
            "user_message": content,
            "ai_message": fallback_answer,
            "error": True
        }

@router.delete("/rooms/{room_id}")
async def delete_chat_room(
    room_id: int,
    request: Request,
    db: Session = Depends(get_db),
):
    user_id = await user_resolver.resolve_user_id(request)

    result = db.execute(
        text("""
            DELETE FROM chat_rooms
            WHERE id = :room_id
              AND user_id = :user_id
        """),
        {
            "room_id": room_id,
            "user_id": user_id,
        },
    )

    db.commit()

    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="삭제할 채팅방을 찾을 수 없습니다.")

    return {
        "status": "ok",
        "message": "채팅방이 삭제되었습니다."
    }


