import {BookingSeatSummary} from "@/components/seats/booking-seat-summary";
import {CompactSeatSummary} from "@/components/seats/compact-seat-summary";
import {calculateTotalPrice} from "@/lib/currency";
import type {GameSeat} from "@/types/game";
import type {ReservationResponse} from "@/types/reservation";

export function SeatSummary({
                                seats,
                                busy,
                                locked,
                                onReserve,
                                reservation,
                                timerTarget,
                                timerExpired = false,
                                onTimerExpire,
                                onCancelReservation,
                                onContinue,
                            }: {
    seats: GameSeat[];
    busy: boolean;
    locked: boolean;
    onReserve: () => void;
    reservation?: ReservationResponse | null;
    timerTarget?: string | null;
    timerExpired?: boolean;
    onTimerExpire?: () => void;
    onCancelReservation?: () => void;
    onContinue?: () => void;
}) {
    const total = calculateTotalPrice(seats);

    if (!onContinue) {
        return (
            <CompactSeatSummary
                busy={busy}
                locked={locked}
                onReserve={onReserve}
                seats={seats}
                total={total}
            />
        );
    }

    return (
        <BookingSeatSummary
            busy={busy}
            locked={locked}
            onCancelReservation={onCancelReservation}
            onContinue={onContinue}
            onReserve={onReserve}
            onTimerExpire={onTimerExpire}
            reservation={reservation}
            seats={seats}
            timerExpired={timerExpired}
            timerTarget={timerTarget}
            total={total}
        />
    );
}
