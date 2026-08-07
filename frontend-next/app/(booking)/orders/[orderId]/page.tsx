"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";

import { Alert } from "@/components/common/alert";
import { CheckoutScreen } from "@/components/orders/checkout-screen";
import { getGame } from "@/api/games";
import { gameKeys } from "@/api/query-keys/games";
import { useOrder } from "@/hooks/use-order";
import { usePayment } from "@/hooks/use-payment";
import { savePendingPayment } from "@/lib/payment-storage";
import { useBookingStore } from "@/providers/booking-store-provider";

/**
 * 생성된 주문의 상세와 결제 준비를 담당한다.
 *
 * `/checkout`과 같은 화면 컴포넌트를 쓰지만 데이터 책임은 서로 다르다.
 * 이 라우트는 URL의 주문 ID가 정본이고, 좌석 목록은 서버 주문 응답에
 * 포함되지 않으므로 예매 스토어의 선택 좌석 snapshot을 그대로 사용한다.
 */
export default function OrderPage() {
  const params = useParams<{ orderId: string }>();
  const router = useRouter();
  const orderId = Number(params.orderId);
  const order = useOrder(orderId);
  const payment = usePayment();
  const gameId = useBookingStore((state) => state.selectedGameId);
  const seats = useBookingStore((state) => state.selectedSeats);

  const game = useQuery({
    queryKey: gameKeys.detail(gameId ?? 0),
    queryFn: () => getGame(gameId as number),
    enabled: gameId != null,
  });

  if (!Number.isSafeInteger(orderId) || orderId <= 0) {
    return <Alert message="올바르지 않은 주문 주소입니다." variant="error" />;
  }
  if (order.detail.isError) {
    return <Alert message={order.detail.error.message} variant="error" />;
  }
  if (!order.detail.data) return <p>주문을 불러오고 있습니다.</p>;

  return (
    <CheckoutScreen
      busy={order.cancel.isPending || payment.prepare.isPending}
      game={game.data ?? null}
      onBack={() => router.back()}
      onCancelOrder={() => order.cancel.mutate(orderId)}
      onPayment={() =>
        payment.prepare.mutate(orderId, {
          onSuccess: ({ payment: prepared, idempotencyKey }) => {
            // PG 왕복 중에는 브라우저 저장소만 남으므로 복귀 시 필요한 값을 먼저 저장한다.
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
      onRefreshOrder={() => {
        void order.detail.refetch();
      }}
      order={order.detail.data}
      seats={seats}
    />
  );
}
