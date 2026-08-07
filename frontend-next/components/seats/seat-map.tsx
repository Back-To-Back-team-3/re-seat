import type { GameSeat } from "@/types/game";

export function SeatMap({
  seats,
  selectedSeats,
  locked,
  onToggle,
}: {
  seats: GameSeat[];
  selectedSeats: GameSeat[];
  locked: boolean;
  onToggle: (seat: GameSeat) => void;
}) {
  return (
    <section className="grid grid-cols-[repeat(auto-fill,minmax(64px,1fr))] gap-2">
      {seats.map((seat) => {
        const selected = selectedSeats.some(
          (candidate) => candidate.gameSeatId === seat.gameSeatId,
        );
        const unavailable = seat.status !== "AVAILABLE";
        return (
          <button
            aria-pressed={selected}
            className={`min-h-14 rounded-control border text-xs font-bold ${
              selected
                ? "border-brand bg-brand text-white"
                : "border-border bg-surface"
            }`}
            disabled={
              locked ||
              unavailable ||
              (!selected && selectedSeats.length >= 2)
            }
            key={seat.gameSeatId}
            onClick={() => onToggle(seat)}
            type="button"
          >
            {seat.seatRow}열 {seat.seatNumber}번
          </button>
        );
      })}
    </section>
  );
}
