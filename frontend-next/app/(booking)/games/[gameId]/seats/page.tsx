"use client";

import { useParams, useRouter } from "next/navigation";

import { SeatMap } from "@/components/seats/seat-map";
import { SeatSummary } from "@/components/seats/seat-summary";
import { useReservation } from "@/hooks/use-reservation";
import { useSeats } from "@/hooks/use-seats";
import { useBookingStore } from "@/providers/booking-store-provider";

export default function SeatsPage() {
  const params = useParams<{ gameId: string }>();
  const router = useRouter();
  const gameId = Number(params.gameId);
  const zoneId = useBookingStore((state) => state.selectedZoneId);
  const setZone = useBookingStore((state) => state.setZone);
  const toggleSeat = useBookingStore((state) => state.toggleSeat);
  const selectedSeats = useBookingStore((state) => state.selectedSeats);
  const reservation = useReservation(gameId);
  const { zones, seats } = useSeats(gameId, zoneId);

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
      <section className="grid gap-5">
        <h1 className="text-3xl font-black">좌석 선택</h1>
        <div className="flex flex-wrap gap-2">
          {zones.data?.map((zone) => (
            <button
              className="rounded-control border border-border px-4 py-2"
              key={zone.zoneId}
              onClick={() => setZone(zone.zoneId)}
              type="button"
            >
              {zone.zoneName} ({zone.availableCount})
            </button>
          ))}
        </div>
        <SeatMap
          locked={Boolean(reservation.reservation)}
          onToggle={toggleSeat}
          seats={seats.data ?? []}
          selectedSeats={selectedSeats}
        />
      </section>
      <SeatSummary
        busy={reservation.create.isPending}
        locked={Boolean(reservation.reservation)}
        onReserve={() => {
          reservation.create.mutate(undefined, {
            onSuccess: () => router.push("/checkout"),
          });
        }}
        seats={selectedSeats}
      />
    </div>
  );
}
