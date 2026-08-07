"use client";

import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

import { Countdown } from "@/components/common/countdown";
import { OrderSummary } from "@/components/orders/order-summary";
import { useOrder } from "@/hooks/use-order";
import { usePayment } from "@/hooks/use-payment";
import { savePendingPayment } from "@/lib/payment-storage";
import { useBookingStore } from "@/providers/booking-store-provider";

export default function OrderPage() {
  const params = useParams<{ orderId: string }>();
  const router = useRouter();
  const orderId = Number(params.orderId);
  const order = useOrder(orderId);
  const payment = usePayment();
  const gameId = useBookingStore((state) => state.selectedGameId);
  const seats = useBookingStore((state) => state.selectedSeats);
  const [expired, setExpired] = useState(false);
  if (!order.detail.data) return <p>주문을 불러오고 있습니다.</p>;
  return (
    <div className="grid gap-5">
      <h1 className="text-3xl font-black">주문 정보</h1>
      <OrderSummary order={order.detail.data} />
      <p>
        결제 가능 시간{" "}
        <Countdown
          onExpire={() => setExpired(true)}
          target={order.detail.data.paymentDeadline}
        />
      </p>
      <button
        className="rounded-control bg-brand px-5 py-3 font-bold text-white"
        onClick={() =>
          payment.prepare.mutate(orderId, {
            onSuccess: ({ payment: prepared, idempotencyKey }) => {
              savePendingPayment({
                orderId,
                gameId,
                payment: prepared,
                idempotencyKey,
                seats,
              });
              router.push(`/payments/${prepared.paymentId}`);
            },
          })
        }
        disabled={expired || payment.prepare.isPending}
        type="button"
      >
        결제 준비
      </button>
      {order.detail.data.status === "CREATED" && (
        <button
          className="text-sm text-muted-foreground"
          onClick={() => order.cancel.mutate(orderId)}
          type="button"
        >
          주문 취소
        </button>
      )}
    </div>
  );
}
