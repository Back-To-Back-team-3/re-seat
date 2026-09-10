import type {OrderResponse} from "@/types/order";
import type {PaymentResponse} from "@/types/payment";
import type {HoldTimeResponse} from "@/types/reservation";
import type {BookingData} from "@/stores/booking-store";

type BookingResumeDependencies = {
    getPayment: (paymentId: number) => Promise<Pick<PaymentResponse, "status">>;
    getOrder: (orderId: number) => Promise<Pick<OrderResponse, "status">>;
    getReservationHoldTime: (
        reservationId: number,
    ) => Promise<Pick<HoldTimeResponse, "status" | "remainingSeconds">>;
    queueToken: string | null;
    now?: Date;
};

export type BookingResumeResult = {
    destination: string | null;
    progress: BookingData;
};

function freshProgress(gameId: number | null): BookingData {
    return {
        selectedGameId: gameId,
        selectedZoneId: null,
        selectedSeats: [],
        reservation: null,
        orderId: null,
        paymentId: null,
        queueTokenExpiresAt: null,
    };
}

/** 서버 상태를 우선해 현재 예매에서 이어갈 수 있는 가장 뒤쪽 단계를 찾습니다. */
export async function resolveBookingResume(
    stored: BookingData,
    gameId: number,
    dependencies: BookingResumeDependencies,
): Promise<BookingResumeResult> {
    let progress = {...stored};

    if (progress.paymentId !== null) {
        const payment = await dependencies.getPayment(progress.paymentId);
        if (payment.status === "READY") {
            return {
                destination: `/payments/${progress.paymentId}`,
                progress,
            };
        }
        if (
            payment.status === "APPROVED" ||
            payment.status === "PARTIALLY_CANCELED"
        ) {
            return {destination: "/mypage", progress: freshProgress(null)};
        }
        progress = {...progress, paymentId: null};
    }

    if (progress.orderId !== null) {
        const order = await dependencies.getOrder(progress.orderId);
        if (order.status === "CREATED") {
            return {destination: `/orders/${progress.orderId}`, progress};
        }
        if (order.status === "PAID" || order.status === "PARTIALLY_CANCELED") {
            return {destination: "/mypage", progress: freshProgress(null)};
        }
        return {destination: null, progress: freshProgress(gameId)};
    }

    if (progress.reservation !== null) {
        const hold = await dependencies.getReservationHoldTime(
            progress.reservation.reservationId,
        );
        if (hold.status === "HOLDING" && hold.remainingSeconds > 0) {
            return {destination: "/checkout", progress};
        }
        return {destination: null, progress: freshProgress(gameId)};
    }

    const expiresAt = progress.queueTokenExpiresAt;
    const now = dependencies.now ?? new Date();
    if (
        dependencies.queueToken &&
        progress.selectedGameId !== null &&
        expiresAt &&
        new Date(expiresAt).getTime() > now.getTime()
    ) {
        return {
            destination: `/games/${progress.selectedGameId}/seats`,
            progress,
        };
    }

    // 이어갈 단계가 없을 때만 사용자가 새로 선택한 경기로 진행 상태를 초기화한다.
    return {destination: null, progress: freshProgress(gameId)};
}
