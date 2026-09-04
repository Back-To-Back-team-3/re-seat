import {storage} from "@/lib/storage";
import type {PaymentCreateResponse} from "@/types/payment";

const PENDING_KEY = "pendingTossPayment";
const IDEMPOTENCY_PREFIX = "paymentIdempotencyKey:";
const CALLBACK_PREFIX = "tossPaymentCallback:";

export type PendingPayment = {
    orderId: number;
    gameId: number | null;
    payment: PaymentCreateResponse;
    idempotencyKey: string;
};

export function getPaymentKey(orderId: number) {
    const key = `${IDEMPOTENCY_PREFIX}${orderId}`;
    const stored = storage.session.get(key);
    if (stored) return stored;
    const created =
        typeof crypto.randomUUID === "function"
            ? crypto.randomUUID()
            : `${orderId}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    storage.session.set(key, created);
    return created;
}

export function savePendingPayment(pending: PendingPayment) {
    storage.session.set(PENDING_KEY, JSON.stringify(pending));
}

export function getPendingPayment() {
    return storage.session.getJson<PendingPayment>(PENDING_KEY);
}

export function clearPendingPayment() {
    storage.session.remove(PENDING_KEY);
}

export function beginPaymentCallback(paymentId: number) {
    const key = `${CALLBACK_PREFIX}${paymentId}`;
    if (storage.session.get(key)) return false;
    storage.session.set(key, "processing");
    return true;
}

export function completePaymentCallback(paymentId: number) {
    storage.session.set(`${CALLBACK_PREFIX}${paymentId}`, "completed");
}

export function resetPaymentCallback(paymentId: number) {
    storage.session.remove(`${CALLBACK_PREFIX}${paymentId}`);
}
