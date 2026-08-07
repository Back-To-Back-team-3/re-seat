import { formatPrice } from "@/lib/currency";
import type { GameSeat } from "@/types/game";

export function SeatSummary({
  seats,
  busy,
  locked,
  onReserve,
}: {
  seats: GameSeat[];
  busy: boolean;
  locked: boolean;
  onReserve: () => void;
}) {
  const total = seats.reduce((sum, seat) => sum + seat.price, 0);
  return (
    <aside className="grid gap-4 rounded-panel border border-border bg-surface p-5 shadow-card">
      <strong>선택 좌석 ({seats.length}/2)</strong>
      {seats.map((seat) => (
        <span className="text-sm" key={seat.gameSeatId}>
          {seat.zoneName} {seat.seatRow}열 {seat.seatNumber}번
        </span>
      ))}
      <strong>{formatPrice(total)}</strong>
      <button
        className="rounded-control bg-brand px-5 py-3 font-bold text-white disabled:bg-muted"
        disabled={busy || locked || seats.length === 0}
        onClick={onReserve}
        type="button"
      >
        {locked ? "예약 완료" : "선택 좌석 예약"}
      </button>
    </aside>
  );
}
