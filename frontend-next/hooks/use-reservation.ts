"use client";

import {useMutation, useQueryClient} from "@tanstack/react-query";

import {gameKeys} from "@/api/query-keys/games";
import {cancelReservation, createReservation, getReservationHoldTime,} from "@/api/reservations";
import {storage} from "@/lib/storage";
import {useBookingStore} from "@/providers/booking-store-provider";

export function useReservation(gameId: number) {
    const queryClient = useQueryClient();
    const selectedSeats = useBookingStore((state) => state.selectedSeats);
    const reservation = useBookingStore((state) => state.reservation);
    const setReservation = useBookingStore((state) => state.setReservation);
    const clearSeats = useBookingStore((state) => state.clearSeats);
    const setQueueExpiry = useBookingStore((state) => state.setQueueExpiry);

    const create = useMutation({
        mutationFn: async () => {
            const created = await createReservation(
                gameId,
                selectedSeats.map((seat) => seat.gameSeatId),
            );
            // 예약이 좌석 선점을 소유한 뒤에는 대기열 토큰을 먼저 제거한다.
            // 입장 토큰은 1회용이므로 여기서 소비되고, 예약을 취소하더라도 되살아나지
            // 않는다. 화면이 이 사실을 알 수 있도록 만료 시각도 함께 비운다.
            storage.local.remove("queueToken");
            setQueueExpiry(null);
            // 이어서 서버의 실제 잔여 시간을 확인해 만료된 예약으로 주문하지 않게 한다.
            const holdTime = await getReservationHoldTime(created.reservationId);
            return {reservation: created, holdTime};
        },
        onSuccess: ({reservation: created}) => {
            setReservation(created);
        },
    });

    const cancel = useMutation({
        mutationFn: () => cancelReservation(reservation!.reservationId),
        onSuccess: async () => {
            setReservation(null);
            clearSeats();
            await queryClient.invalidateQueries({
                queryKey: gameKeys.seats(gameId),
            });
        },
    });

    return {selectedSeats, reservation, create, cancel};
}
