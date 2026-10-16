import { corsHeaders, errorMessage, json } from "../_shared/http.ts";
import { requireUser } from "../_shared/supabase.ts";

type PaymentRequest = {
  action: "prepare" | "confirm";
  plan_id?: number;
  payment_key?: string;
  order_id?: string;
  amount?: number;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const { user, adminClient } = await requireUser(req);
    const body = (await req.json()) as PaymentRequest;
    const clientKey = Deno.env.get("TOSS_CLIENT_KEY");
    const secretKey = Deno.env.get("TOSS_SECRET_KEY");
    if (!clientKey || !secretKey) throw new Error("Toss secrets are missing.");

    if (body.action === "prepare") {
      const { data: plan, error: planError } = await adminClient
        .from("plans").select("id,name,price,currency").eq("id", body.plan_id).eq("is_active", true).single();
      if (planError || !plan) return json({ error: "존재하지 않는 상품입니다." }, 404);
      const orderId = `order_${crypto.randomUUID().replaceAll("-", "").slice(0, 24)}`;
      const { error: orderError } = await adminClient.from("orders").insert({
        user_id: user.id,
        plan_id: plan.id,
        order_id: orderId,
        amount: plan.price,
        currency: plan.currency,
        status: "ready",
        provider: "toss",
      });
      if (orderError) throw orderError;
      return json({
        order_id: orderId,
        amount: plan.price,
        order_name: plan.name,
        client_key: clientKey,
        customer_key: `user_${user.id}`,
      });
    }

    if (body.action === "confirm") {
      const { data: order, error: orderError } = await adminClient.from("orders")
        .select("id,user_id,order_id,plan_id,amount,currency,status")
        .eq("order_id", body.order_id).eq("user_id", user.id).single();
      if (orderError || !order) return json({ error: "주문을 찾을 수 없습니다." }, 404);
      if (order.status !== "ready") return json({ error: "이미 처리된 주문입니다." }, 409);
      if (order.amount !== body.amount || !body.payment_key) return json({ error: "결제 정보가 주문과 일치하지 않습니다." }, 400);

      const basic = btoa(`${secretKey}:`);
      const tossResponse = await fetch("https://api.tosspayments.com/v1/payments/confirm", {
        method: "POST",
        headers: { Authorization: `Basic ${basic}`, "Content-Type": "application/json" },
        body: JSON.stringify({ paymentKey: body.payment_key, orderId: body.order_id, amount: body.amount }),
      });
      const toss = await tossResponse.json();
      if (!tossResponse.ok) return json({ error: "결제 승인에 실패했습니다.", toss }, 400);

      const { error: paymentError } = await adminClient.from("payments").insert({
        user_id: user.id,
        provider: "toss",
        provider_payment_id: body.payment_key,
        amount: order.amount,
        currency: order.currency,
        status: "paid",
        payment_type: "one_time",
        paid_at: new Date().toISOString(),
      });
      if (paymentError) throw paymentError;
      const { error: updateError } = await adminClient.from("orders").update({
        status: "paid",
        provider_payment_key: body.payment_key,
        updated_at: new Date().toISOString(),
      }).eq("id", order.id).eq("status", "ready");
      if (updateError) throw updateError;
      return json({ status: "ok", order_id: order.order_id, payment_key: body.payment_key, amount: order.amount });
    }

    return json({ error: "지원하지 않는 action입니다." }, 400);
  } catch (error) {
    console.error(errorMessage(error));
    return json({ error: errorMessage(error) }, 400);
  }
});
