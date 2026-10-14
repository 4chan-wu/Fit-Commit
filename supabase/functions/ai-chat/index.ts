import { corsHeaders, errorMessage, json } from "../_shared/http.ts";
import { requireUser } from "../_shared/supabase.ts";

type ChatRequest = { room_id: number; content: string };

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const { user, userClient, adminClient } = await requireUser(req);
    const body = (await req.json()) as ChatRequest;
    const content = body.content?.trim();
    if (!Number.isInteger(body.room_id) || !content) return json({ error: "room_id와 content가 필요합니다." }, 400);
    if (content.length > 1000) return json({ error: "메시지는 1000자 이하로 입력해주세요." }, 400);

    const { data: room, error: roomError } = await userClient
      .from("chat_rooms").select("id,title").eq("id", body.room_id).single();
    if (roomError || !room) return json({ error: "채팅방을 찾을 수 없습니다." }, 404);

    const { error: insertError } = await userClient.from("chat_messages").insert({
      chat_room_id: body.room_id,
      sender_type: "user",
      message_type: "text",
      content,
    });
    if (insertError) throw insertError;

    if (!room.title) {
      await userClient.from("chat_rooms").update({ title: content.slice(0, 30) }).eq("id", body.room_id);
    }

    const { data: messages, error: messagesError } = await userClient
      .from("chat_messages").select("sender_type,content").eq("chat_room_id", body.room_id)
      .order("id", { ascending: false }).limit(20);
    if (messagesError) throw messagesError;

    const apiKey = Deno.env.get("OPENAI_API_KEY");
    if (!apiKey) throw new Error("OPENAI_API_KEY secret is missing.");
    const input = (messages ?? []).reverse().map((message) => ({
      role: message.sender_type === "assistant" ? "assistant" : "user",
      content: message.content,
    }));
    const openAIResponse = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        max_tokens: 300,
        messages: [{ role: "system", content: "You are a concise Korean fashion coordinator." }, ...input],
      }),
    });
    if (!openAIResponse.ok) throw new Error(`OpenAI request failed: ${openAIResponse.status}`);
    const openAI = await openAIResponse.json();
    const answer = openAI.choices?.[0]?.message?.content?.trim();
    if (!answer) throw new Error("OpenAI response was empty.");

    const { error: aiInsertError } = await userClient.from("chat_messages").insert({
      chat_room_id: body.room_id,
      sender_type: "assistant",
      message_type: "text",
      content: answer,
      model_name: "gpt-4o-mini",
    });
    if (aiInsertError) throw aiInsertError;
    await userClient.from("chat_rooms").update({ updated_at: new Date().toISOString() }).eq("id", body.room_id);
    await adminClient.from("ai_request_logs").insert({
      user_id: user.id,
      chat_room_id: body.room_id,
      request_type: "chat",
      model_name: "gpt-4o-mini",
      request_payload: { room_id: body.room_id },
      response_payload: { success: true },
      success: true,
    });
    return json({ user_message: content, ai_message: answer, error: false });
  } catch (error) {
    console.error(errorMessage(error));
    return json({ error: errorMessage(error) }, 400);
  }
});
