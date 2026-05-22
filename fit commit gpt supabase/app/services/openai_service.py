import os

from openai import OpenAI

MODEL_NAME = "gpt-4o-mini"


def call_openai_chat(messages: list[dict]) -> str:
    api_key = os.getenv("OPENAI_API_KEY")

    if not api_key:
        raise ValueError("OPENAI_API_KEY is not set.")

    client = OpenAI(api_key=api_key)

    response = client.responses.create(
        model=MODEL_NAME,
        input=messages,
        max_output_tokens=300,
    )

    return response.output_text