import {Button} from "@/components/ui/button";
import {formatPrice} from "@/lib/currency";
import type {GameSeat} from "@/types/game";

type CompactSeatSummaryProps = {
    seats: GameSeat[];
    total: number;
    busy: boolean;
    locked: boolean;
    onReserve: () => void;
};

/** 예매 단계 제어가 필요 없는 화면에서 선택 좌석과 합계만 간단히 표시한다. */
export function CompactSeatSummary({
    seats,
    total,
    busy,
    locked,
    onReserve,
}: CompactSeatSummaryProps) {
    return (
        <aside className="grid gap-4 rounded-panel border border-border bg-surface p-5 shadow-card">
            <strong>선택 좌석 ({seats.length}/2)</strong>
            {seats.map((seat) => (
                <span className="text-sm" key={seat.gameSeatId}>
                    {seat.zoneName} {seat.seatRow}열 {seat.seatNumber}번
                </span>
            ))}
            <strong>{formatPrice(total)}</strong>
            <Button
                className="w-full"
                disabled={busy || locked || seats.length === 0}
                onClick={onReserve}
                type="button"
            >
                {locked ? "예약 완료" : "선택 좌석 예약"}
            </Button>
        </aside>
    );
}
