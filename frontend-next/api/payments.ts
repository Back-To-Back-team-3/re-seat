import { apiRequest, unwrap } from "@/api/client";
import type { ApiResponse } from "@/types/api";
import type {
  PaymentActionResponse,
  PaymentCreateResponse,
  PaymentResponse,
} from "@/types/payment";

export async function requestPayment(orderId: number, idempotencyKey: string) {
  const response = await apiRequest<ApiResponse<PaymentCreateResponse>>(
    "/payments",
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ orderId }),
    },
  );
  return unwrap(response);
}

export async function completePayment(
  paymentId: number,
  idempotencyKey: string,
  payload: { paymentKey: string; orderId: string; amount: number },
) {
  const response = await apiRequest<ApiResponse<PaymentActionResponse>>(
    `/payments/${paymentId}/complete`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(payload),
    },
  );
  return unwrap(response);
}

export async function failPayment(
  paymentId: number,
  idempotencyKey: string,
  payload: { code: string; message: string; orderId: string },
) {
  const response = await apiRequest<ApiResponse<PaymentActionResponse>>(
    `/payments/${paymentId}/fail`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(payload),
    },
  );
  return unwrap(response);
}

export async function getPayment(paymentId: number) {
  const response = await apiRequest<ApiResponse<PaymentResponse>>(
    `/payments/${paymentId}`,
  );
  return unwrap(response);
}
