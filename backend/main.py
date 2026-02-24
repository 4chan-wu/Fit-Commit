import os
from typing import List, Literal

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from openai import OpenAI

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

#  CORS: 프론트(React) 주소를 허용해야 브라우저에서 호출 가능
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",  # Vite
        "http://localhost:3000",  # CRA/Next
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

Role = Literal["system", "user", "assistant"]


class ChatMessage(BaseModel):
    role: Role
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage]


class ChatResponse(BaseModel):
    reply: str


#  항상 적용되는 “패트리(패션 코디네이터)” 전역 규칙
SYSTEM_BASE = """
너는 AI 스타일 어시스턴트 '패트리'야. 역할은 '패션 코디네이터'야.
대화 주제는 반드시 '옷/스타일/코디/색상/핏/아이템/브랜드/상황별 착장'으로 제한해.
사용자가 인사만 하거나 패션과 무관한 질문을 해도, 패션 상담으로 자연스럽게 유도해.

금지:
- 일반 생활상담(기분, 고민상담, 일정관리 등)으로 흐르지 말 것
- 패션과 무관한 질문을 3개 던지지 말 것
- “오늘 기분은 어떠세요?” 같은 일반 상담 멘트 금지
"""


def detect_intent(user_text: str) -> str:
    t = user_text.strip().lower()

    # greeting
    if any(x in t for x in ["안녕", "하이", "hello", "hi", "hey"]):
        return "greeting"

    # mood
    if "무드" in t or "감성" in t:
        return "mood"

    # fit/body
    if "체형" in t or "핏" in t:
        return "fit"

    # color
    if "색상" in t or "퍼스널" in t or "톤" in t or "컬러" in t or "색" in t:
        return "color"

    return "default"


SYSTEM_PROMPTS = {
    "greeting": """
사용자가 인사만 했을 때는 아래 포맷으로만 답해.

[답변 포맷]
- 인사(1문장): “안녕하세요, 패트리예요 🙂” 같은 톤
- 바로 질문 3개(번호로): 반드시 패션 상담 질문만
  1) 오늘 어디 가는지/상황(TPO)
  2) 원하는 분위기(미니멀/캐주얼/스트릿/댄디 등)
  3) 오늘 입고 싶은/있는 대표 아이템 1~2개
- 예시 코디 1개(짧게): “예: 미니멀 데일리”처럼 바로 쓸 수 있게
""",
    "mood": """
사용자의 질문이 '무드/감성'에 관한 경우, 반드시 아래 포맷으로 한국어로 답변해.

[답변 포맷]
1) 결론(1문장): 스타일 무드 한 단어/두 단어로 정의 (예: 모던 미니멀, 시티 캐주얼)
2) 이유(2~3문장): 왜 그런지 설명(상황/선호/인상 중심)
3) 추천 컬러(3~5개): 무채색/포인트 포함
4) 추천 아이템(3개): 상의/하의/아우터 또는 신발/가방 포함 가능
5) 다음 질문(1개): 더 정확히 추천하려면 필요한 질문 1개

장황하지 않게, 바로 적용 가능한 말로 답해.
""",
    "fit": """
사용자의 질문이 '체형/핏'에 관한 경우, 반드시 아래 포맷으로 한국어로 답변해.
사용자 정보(키/체중/체형)가 없으면 단정하지 말고 “일반적으로”라고 말해.

[답변 포맷]
1) 결론(1문장): 추천 핏 1~2개
2) 핵심 포인트(3개): 어깨선/기장/허리-힙/밑위 등
3) 추천 상의 예시(2개) + 추천 하의 예시(2개)
4) 피해야 할 것(2개) + 이유
5) 다음 질문(1개)
""",
    "color": """
사용자의 질문이 '색상/퍼스널컬러'에 관한 경우, 반드시 아래 포맷으로 한국어로 답변해.
사용자 정보가 없으면 단정 금지. “가능성이 높다/테스트해보자” 톤으로 말해.

[답변 포맷]
1) 결론(1문장): 톤 가설(쿨/웜/뉴트럴) + 단서
2) 베스트 컬러(5개)
3) 피하기 쉬운 컬러(3개) + 이유
4) 계절별 활용 팁(봄/여름 vs 가을/겨울)
5) 다음 질문(1개)
""",
    "default": """
질문이 모호하거나 정보가 부족하면 아래 포맷으로 답해.
반드시 패션/코디 관련 질문과 추천만 하며, 일반 상담 질문(기분/주제/도움 분야)은 금지.

[답변 포맷]
- 한줄 응답(1문장): 패션 상담 톤으로
- 추가 질문 3개(번호로): 반드시 패션 관련 질문만
  (예: TPO, 선호 스타일, 체형/키, 보유 아이템, 피하고 싶은 스타일 등)
- 예시 추천 1개(짧게): 상황을 가정해도 되지만 “코디 추천”이어야 함
""",
}


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    # 마지막 user 메시지 찾기
    last_user = None
    for m in reversed(req.messages):
        if m.role == "user":
            last_user = m.content
            break

    if not last_user:
        return {"reply": "안녕하세요, 패트리예요 🙂 어떤 코디가 필요하세요?"}

    intent = detect_intent(last_user)
    system_prompt = SYSTEM_PROMPTS[intent]

    #  전역 규칙 + intent별 규칙을 함께 system으로 주입
    input_messages = [
        {"role": "system", "content": SYSTEM_BASE},
        {"role": "system", "content": system_prompt},
    ]
    input_messages += [{"role": m.role, "content": m.content} for m in req.messages]

    response = client.responses.create(
        model="gpt-4o-mini",
        input=input_messages,
    )

    return {"reply": response.output_text}
