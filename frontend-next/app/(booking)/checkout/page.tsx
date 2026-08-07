"use client";

import { useRouter } from "next/navigation";

import { CheckoutScreen } from "@/components/orders/checkout-screen";
import { useOrder } from "@/hooks/use-order";
import { useReservation } from "@/hooks/use-reservation";
import { useBookingStore } from "@/providers/booking-store-provider";

export default function CheckoutPage() {
  const router = useRouter();
  const reservation = useBookingStore((state) => state.reservation);
  const seats = useBookingStore((state) => state.selectedSeats);
  const gameId = useBookingStore((state) => state.selectedGameId);
  const order = useOrder();
  const reservationFlow = useReservation(gameId ?? 0);
  return (
    <CheckoutScreen
      busy={order.create.isPending || reservationFlow.cancel.isPending}
      onCreate={() => {
        if (!reservation) return;
        order.create.mutate(reservation.reservationId, {
          onSuccess: (created) => router.push(`/orders/${created.orderId}`),
        });
      }}
      onCancel={() => {
        if (!reservation || !gameId) return;
        reservationFlow.cancel.mutate(undefined, {
          onSuccess: () => router.push(`/games/${gameId}/seats`),
        });
      }}
      reservation={reservation}
      seats={seats}
    />
  );
}
