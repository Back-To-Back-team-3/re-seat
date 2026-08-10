import type {GameSeatStatus} from "@/types/game";

export type ReservationStatus =
    | "HOLDING"
    | "CONFIRMED"
    | "CANCELED"
    | "EXPIRED";

export type ReservationResponse = {
    reservationId: number;
    reservationNo: string;
    status: ReservationStatus;
    gameSeats: Array<{
        gameSeatId: number;
        status: GameSeatStatus;
        price: number;
    }>;
    holdExpiresAt: string;
    gameAt: string;
};

export type HoldTimeResponse = {
    reservationId: number;
    remainingSeconds: number;
    status: ReservationStatus;
    expiresAt: string;
};
