import { createStore } from "zustand/vanilla";

import type { GameSeat } from "@/types/game";
import type { ReservationResponse } from "@/types/reservation";

type BookingData = {
  selectedGameId: number | null;
  selectedZoneId: number | null;
  selectedSeats: GameSeat[];
  reservation: ReservationResponse | null;
  orderId: number | null;
  paymentId: number | null;
  queueTokenExpiresAt: string | null;
};

type BookingActions = {
  setGame: (gameId: number | null) => void;
  setZone: (zoneId: number | null) => void;
  toggleSeat: (seat: GameSeat) => void;
  clearSeats: () => void;
  setReservation: (reservation: ReservationResponse | null) => void;
  setOrderId: (orderId: number | null) => void;
  setPaymentId: (paymentId: number | null) => void;
  setQueueExpiry: (expiresAt: string | null) => void;
  reset: () => void;
};

export type BookingState = BookingData & BookingActions;

const initialBookingState: BookingData = {
  selectedGameId: null,
  selectedZoneId: null,
  selectedSeats: [],
  reservation: null,
  orderId: null,
  paymentId: null,
  queueTokenExpiresAt: null,
};

/**
 * 한 번의 예매 흐름에서만 사용할 독립적인 Zustand store를 만든다.
 *
 * 모듈 전역 singleton으로 만들지 않아 BookingStoreProvider가 사라지면 진행 상태도
 * 함께 폐기된다. persist도 사용하지 않으므로 새로고침 시 초기화되는 기존 동작을 유지한다.
 */
export function createBookingStore() {
  return createStore<BookingState>()((set) => ({
    ...initialBookingState,
    setGame: (selectedGameId) => set({ selectedGameId }),
    setZone: (selectedZoneId) => set({ selectedZoneId }),
    toggleSeat: (seat) =>
      set((state) => {
        const alreadySelected = state.selectedSeats.some(
          (selectedSeat) => selectedSeat.gameSeatId === seat.gameSeatId,
        );

        // 같은 좌석을 다시 누르면 해제하고, 처음 누른 좌석이면 기존 선택 뒤에 추가한다.
        if (state.reservation || (!alreadySelected && state.selectedSeats.length >= 2)) {
          return state;
        }
        return {
          selectedSeats: alreadySelected
            ? state.selectedSeats.filter(
                (selectedSeat) =>
                  selectedSeat.gameSeatId !== seat.gameSeatId,
              )
            : [...state.selectedSeats, seat],
        };
      }),
    clearSeats: () => set({ selectedSeats: [] }),
    setReservation: (reservation) => set({ reservation }),
    setOrderId: (orderId) => set({ orderId }),
    setPaymentId: (paymentId) => set({ paymentId }),
    setQueueExpiry: (queueTokenExpiresAt) => set({ queueTokenExpiresAt }),
    reset: () => set(initialBookingState),
  }));
}

export type BookingStore = ReturnType<typeof createBookingStore>;
