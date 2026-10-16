import base64
import os
import uuid
from uuid import UUID

import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session


TOSS_API_BASE = "https://api.tosspayments.com"


def _basic_auth_header(secret_key: str) -> dict:
    encoded = base64.b64encode(f"{secret_key}:".encode("utf-8")).decode("utf-8")
    return {
        "Authorization": f"Basic {encoded}",
        "Content-Type": "application/json",
    }


class TossPaymentService:
    def __init__(self):
        self.client_key = os.getenv("TOSS_CLIENT_KEY")
        self.secret_key = os.getenv("TOSS_SECRET_KEY")

        if not self.client_key or not self.secret_key:
            raise ValueError("TOSS_CLIENT_KEY / TOSS_SECRET_KEY not set")

    def create_order(self, db: Session, user_id: UUID, plan_id: int) -> dict:
        plan = db.execute(
            text("""
                SELECT id, code, name, price, currency
                FROM plans
                WHERE id = :plan_id AND is_active = true
            """),
            {"plan_id": plan_id},
        ).mappings().first()

        if not plan:
            raise ValueError("존재하지 않는 상품입니다.")

        order_id = f"order_{uuid.uuid4().hex[:24]}"
        customer_key = f"user_{user_id}"

        db.execute(
            text("""
                INSERT INTO orders (
                    user_id, plan_id, order_id, amount, currency, status, provider
                )
                VALUES (
                    :user_id, :plan_id, :order_id, :amount, :currency, 'ready', 'toss'
                )
            """),
            {
                "user_id": user_id,
                "plan_id": plan["id"],
                "order_id": order_id,
                "amount": plan["price"],
                "currency": plan["currency"],
            },
        )
        db.commit()

        return {
            "order_id": order_id,
            "amount": plan["price"],
            "order_name": plan["name"],
            "client_key": self.client_key,
            "customer_key": customer_key,
        }

    async def confirm_payment(
        self,
        db: Session,
        user_id: UUID,
        payment_key: str,
        order_id: str,
        amount: int,
    ) -> dict:
        order = db.execute(
            text("""
                SELECT o.id, o.user_id, o.plan_id, o.order_id, o.amount, o.currency, o.status,
                       p.name AS plan_name
                FROM orders o
                JOIN plans p ON p.id = o.plan_id
                WHERE o.order_id = :order_id AND o.user_id = :user_id
            """),
            {"order_id": order_id, "user_id": user_id},
        ).mappings().first()

        if not order:
            raise ValueError("주문을 찾을 수 없습니다.")

        if order["status"] != "ready":
            raise ValueError("이미 처리된 주문입니다.")

        if order["amount"] != amount:
            raise ValueError("결제 금액이 주문 금액과 일치하지 않습니다.")

        headers = _basic_auth_header(self.secret_key)

        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(
                f"{TOSS_API_BASE}/v1/payments/confirm",
                headers=headers,
                json={
                    "paymentKey": payment_key,
                    "orderId": order_id,
                    "amount": amount,
                },
            )
            response.raise_for_status()
            payment_data = response.json()

        db.execute(
            text("""
                INSERT INTO payments (
                    user_id, provider, provider_payment_id, amount, currency,
                    status, payment_type, paid_at
                )
                VALUES (
                    :user_id, 'toss', :provider_payment_id, :amount, :currency,
                    'paid', 'one_time', NOW()
                )
            """),
            {
                "user_id": user_id,
                "provider_payment_id": payment_key,
                "amount": amount,
                "currency": order["currency"],
            },
        )

        db.execute(
            text("""
                UPDATE orders
                SET status = 'paid',
                    provider_payment_key = :payment_key,
                    updated_at = NOW()
                WHERE order_id = :order_id
            """),
            {
                "payment_key": payment_key,
                "order_id": order_id,
            },
        )

        db.commit()

        return {
            "status": "ok",
            "order_id": order_id,
            "payment_key": payment_key,
            "amount": amount,
            "toss_response": payment_data,
        }
