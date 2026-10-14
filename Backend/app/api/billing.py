from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.billing import (
    PlanResponse,
    PreparePaymentRequest,
    PreparePaymentResponse,
    ConfirmPaymentRequest,
)
from app.services.toss_payment_service import TossPaymentService
from app.services.supabase_auth_service import user_resolver

router = APIRouter(prefix="/billing", tags=["billing"])


@router.get("/plans", response_model=list[PlanResponse])
def get_plans(db: Session = Depends(get_db)):
    rows = db.execute(
        text("""
            SELECT id, code, name, price, currency
            FROM plans
            WHERE is_active = true
            ORDER BY id ASC
        """)
    ).mappings().all()
    return rows


@router.post("/payments/prepare", response_model=PreparePaymentResponse)
async def prepare_payment(
    body: PreparePaymentRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    user_id = await user_resolver.resolve_user_id(request)
    service = TossPaymentService()

    try:
        return service.create_order(db=db, user_id=user_id, plan_id=body.plan_id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/payments/confirm")
async def confirm_payment(
    body: ConfirmPaymentRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    user_id = await user_resolver.resolve_user_id(request)
    service = TossPaymentService()

    try:
        return await service.confirm_payment(
            db=db,
            user_id=user_id,
            payment_key=body.payment_key,
            order_id=body.order_id,
            amount=body.amount,
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
