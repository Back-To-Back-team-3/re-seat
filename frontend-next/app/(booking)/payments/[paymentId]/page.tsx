"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";

import { getGame } from "@/api/games";
import { gameKeys } from "@/api/query-keys/games";
import { Alert } from "@/components/common/alert";
import { PaymentScreen } from "@/components/payments/payment-screen";
import { useOrder } from "@/hooks/use-order";
import { usePayment } from "@/hooks/use-payment";
import { openTossPayment } from "@/lib/payment-sdk";
import { getPendingPayment } from "@/lib/payment-storage";
import { useBookingStore } from "@/providers/booking-store-provider";

/**
 * 결제 준비 상태와 승인 완료를 보여주고 PG 결제창 진입을 담당한다.
 *
 * 결제 기한과 주문번호는 결제 응답이 아니라 주문에 있으므로 주문도 함께
 * 조회한다. PG 왕복에 필요한 멱등키와 pgOrderId는 브라우저 저장소의
 * pending 값에만 있으므로, 현재 URL의 결제 ID와 일치할 때만 결제창을 연다.
 */
export default function PaymentPage() {
  const params = useParams<{ paymentId: string }>();
  const paymentId = Number(params.paymentId);

  // 잘못된 주소는 서버 조회 자체가 의미 없으므로 훅을 걸기 전에 끝낸다.
  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) {
    return <Alert message="올바르지 않은 결제 주소입니다." variant="error" />;
  }

  return <PaymentDetail paymentId={paymentId} />;
}

function PaymentDetail({ paymentId }: { paymentId: number }) {
  const router = useRouter();
  const payment = usePayment(paymentId);
  const pending = getPendingPayment();
  const orderId = pending?.orderId;
  const order = useOrder(orderId);
  const gameId = useBookingStore((state) => state.selectedGameId);

  const game = useQuery({
    queryKey: gameKeys.detail(gameId ?? 0),
    queryFn: () => getGame(gameId as number),
    enabled: gameId != null,
  });

  return (
    <PaymentScreen
      busy={payment.detail.isFetching}
      error={payment.detail.error?.message ?? null}
      game={game.data ?? null}
      onBack={() => router.back()}
      onOpenPayment={() => {
        const data = payment.detail.data;
        const stored = getPendingPayment();
        if (!data || !stored || stored.payment.paymentId !== paymentId) return;
        const baseUrl = window.location.origin + window.location.pathname;
        void openTossPayment({
          amount: data.amount,
          orderId: stored.payment.pgOrderId,
          orderName: "Re:Seat 티켓",
          customerName: "Re:Seat 사용자",
          successUrl: `${baseUrl}?paymentId=${paymentId}`,
          failUrl: `${baseUrl}?paymentId=${paymentId}`,
        });
      }}
      onRefreshOrder={() => {
        void order.detail.refetch();
      }}
      onTickets={() => router.push("/tickets")}
      order={order.detail.data ?? null}
      payment={payment.detail.data ?? null}
    />
  );
}
