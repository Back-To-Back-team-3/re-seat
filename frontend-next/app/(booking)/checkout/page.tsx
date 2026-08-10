"use client";

import {useQuery} from "@tanstack/react-query";
import {useRouter} from "next/navigation";

import {getGame} from "@/api/games";
import {gameKeys} from "@/api/query-keys/games";
import {CheckoutScreen} from "@/components/orders/checkout-screen";
import {useOrder} from "@/hooks/use-order";
import {useBookingStore} from "@/providers/booking-store-provider";

/**
 * 좌석 선점 직후 주문을 만들기 전 단계를 보여준다.
 *
 * 예약 전체를 다시 조회하는 API가 없으므로 선택 좌석과 예약 응답은 예매
 * 스토어의 snapshot을 사용한다. 새로고침하면 snapshot이 사라지고 화면이
 * 안전한 안내로 돌아가는 기존 동작을 그대로 유지한다.
 */
export default function CheckoutPage() {
    const router = useRouter();
    const reservation = useBookingStore((state) => state.reservation);
    const seats = useBookingStore((state) => state.selectedSeats);
    const gameId = useBookingStore((state) => state.selectedGameId);
    const order = useOrder();

    const game = useQuery({
        queryKey: gameKeys.detail(gameId ?? 0),
        queryFn: () => getGame(gameId as number),
        enabled: gameId != null,
    });

    return (
        <CheckoutScreen
            busy={order.create.isPending}
            game={game.data ?? null}
            onBack={() => {
                if (!gameId) return;
                router.push(`/games/${gameId}/seats`);
            }}
            onCreateOrder={() => {
                if (!reservation) return;
                order.create.mutate(reservation.reservationId, {
                    onSuccess: (created) => router.push(`/orders/${created.orderId}`),
                });
            }}
            order={null}
            reservation={reservation}
            seats={seats}
        />
    );
}
