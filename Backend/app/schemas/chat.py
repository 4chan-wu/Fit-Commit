from pydantic import BaseModel
from typing import Optional


class CreateChatRoomRequest(BaseModel):
    title: Optional[str] = None
    room_type: str = "general"


class ChatRoomResponse(BaseModel):
    id: int
    title: Optional[str]
    room_type: str

    class Config:
        from_attributes = True


class SendMessageRequest(BaseModel):
    content: str


class ChatMessageResponse(BaseModel):
    id: int
    chat_room_id: int
    sender_type: str
    message_type: str
    content: str
    model_name: Optional[str] = None

    class Config:
        from_attributes = True