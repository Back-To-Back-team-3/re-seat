"use client";

import { useParams } from "next/navigation";

import { PaymentScreen } from "@/components/payments/payment-screen";
import { usePayment } from "@/hooks/use-payment";
import { openTossPayment } from "@/lib/payment-sdk";
import { getPendingPayment } from "@/lib/payment-storage";

export default function PaymentPage() {
  const params = useParams<{ paymentId: string }>();
  const paymentId = Number(params.paymentId);
  const payment = usePayment(paymentId);
  return (
    <PaymentScreen
      onPay={() => {
        const data = payment.detail.data;
        const pending = getPendingPayment();
        if (!data || !pending || pending.payment.paymentId !== paymentId) return;
        const baseUrl = window.location.origin + window.location.pathname;
        void openTossPayment({
          amount: data.amount,
          orderId: pending.payment.pgOrderId,
          orderName: "Re:Seat 티켓",
          customerName: "Re:Seat 사용자",
          successUrl: `${baseUrl}?paymentId=${paymentId}`,
          failUrl: `${baseUrl}?paymentId=${paymentId}`,
        });
      }}
      payment={payment.detail.data ?? null}
    />
  );
}
