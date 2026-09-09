"use client";

import {useRouter} from "next/navigation";
import {useEffect, useState} from "react";

import {getOrder} from "@/api/orders";
import {getPayment} from "@/api/payments";
import {getReservationHoldTime} from "@/api/reservations";
import {resolveBookingResume} from "@/lib/booking-resume";
import {storage} from "@/lib/storage";
import {useBookingStore} from "@/providers/booking-store-provider";

/** 저장된 예매 단계를 서버 상태와 대조한 뒤 이동 또는 새 대기열 진입을 결정합니다. */
export function useBookingResume(gameId: number) {
    const router = useRouter();
    const hydrated = useBookingStore((state) => state.hydrated);
    const selectedGameId = useBookingStore((state) => state.selectedGameId);
    const selectedZoneId = useBookingStore((state) => state.selectedZoneId);
    const selectedSeats = useBookingStore((state) => state.selectedSeats);
    const reservation = useBookingStore((state) => state.reservation);
    const orderId = useBookingStore((state) => state.orderId);
    const paymentId = useBookingStore((state) => state.paymentId);
    const queueTokenExpiresAt = useBookingStore(
        (state) => state.queueTokenExpiresAt,
    );
    const hydrate = useBookingStore((state) => state.hydrate);
    const [restoring, setRestoring] = useState(true);
    const [shouldEnterQueue, setShouldEnterQueue] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!hydrated || !Number.isFinite(gameId)) return;

        let active = true;

        void resolveBookingResume(
            {
                selectedGameId,
                selectedZoneId,
                selectedSeats,
                reservation,
                orderId,
                paymentId,
                queueTokenExpiresAt,
            },
            gameId,
            {
                getPayment,
                getOrder,
                getReservationHoldTime,
                queueToken: storage.local.get("queueToken"),
            },
        )
            .then((result) => {
                if (!active) return;

                hydrate(result.progress);
                if (result.progress.selectedGameId === null) {
                    storage.local.remove("queueToken");
                }
                if (result.destination) {
                    router.replace(result.destination);
                    return;
                }

                // 저장된 진행 상태를 이어갈 수 없을 때만 낡은 입장 토큰을 폐기한다.
                storage.local.remove("queueToken");
                setRestoring(false);
                setShouldEnterQueue(true);
            })
            .catch((cause) => {
                if (!active) return;

                // 서버 확인 실패를 만료로 취급하면 중복 예매가 시작될 수 있으므로 현재 단계에 머문다.
                setError(
                    cause instanceof Error
                        ? cause.message
                        : "예매 진행 상태를 확인하지 못했습니다.",
                );
                setRestoring(false);
            });

        return () => {
            active = false;
        };
    }, [
        gameId,
        hydrate,
        hydrated,
        orderId,
        paymentId,
        queueTokenExpiresAt,
        reservation,
        router,
        selectedGameId,
        selectedSeats,
        selectedZoneId,
    ]);

    return {restoring, shouldEnterQueue, error};
}
