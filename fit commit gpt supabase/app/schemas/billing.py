from pydantic import BaseModel


class PlanResponse(BaseModel):
    id: int
    code: str
    name: str
    price: int
    currency: str

    class Config:
        from_attributes = True


class PreparePaymentRequest(BaseModel):
    plan_id: int


class PreparePaymentResponse(BaseModel):
    order_id: str
    amount: int
    order_name: str
    client_key: str
    customer_key: str


class ConfirmPaymentRequest(BaseModel):
    payment_key: str
    order_id: str
    amount: int