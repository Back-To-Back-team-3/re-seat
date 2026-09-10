import {storage} from "@/lib/storage";
import type {BookingData, BookingState} from "@/stores/booking-store";

const BOOKING_PROGRESS_KEY = "bookingProgress";
const BOOKING_PROGRESS_VERSION = 1;

type StoredBookingProgress = {
    version: number;
    data: BookingData;
};

function isBookingProgress(value: unknown): value is BookingData {
    if (!value || typeof value !== "object") return false;

    const progress = value as Partial<BookingData>;
    return (
        (progress.selectedGameId === null ||
            typeof progress.selectedGameId === "number") &&
        Array.isArray(progress.selectedSeats)
    );
}

/** 현재 탭에서 이어갈 수 있는 예매 진행 상태를 읽습니다. */
export function loadBookingProgress(): BookingData | null {
    const stored = storage.session.getJson<StoredBookingProgress>(
        BOOKING_PROGRESS_KEY,
    );

    if (
        stored?.version !== BOOKING_PROGRESS_VERSION ||
        !isBookingProgress(stored.data)
    ) {
        return null;
    }

    return stored.data;
}

/** 새로고침 후 복원할 예매 진행 상태를 현재 탭에 저장합니다. */
export function saveBookingProgress(progress: BookingData): void {
    storage.session.set(
        BOOKING_PROGRESS_KEY,
        JSON.stringify({version: BOOKING_PROGRESS_VERSION, data: progress}),
    );
}

/** 만료되거나 종료된 예매 진행 상태를 현재 탭에서 제거합니다. */
export function clearBookingProgress(): void {
    storage.session.remove(BOOKING_PROGRESS_KEY);
}

/** Store의 action을 제외하고 저장 가능한 예매 데이터만 추립니다. */
export function selectBookingProgress(state: BookingState): BookingData {
    return {
        selectedGameId: state.selectedGameId,
        selectedZoneId: state.selectedZoneId,
        selectedSeats: state.selectedSeats,
        reservation: state.reservation,
        orderId: state.orderId,
        paymentId: state.paymentId,
        queueTokenExpiresAt: state.queueTokenExpiresAt,
    };
}
