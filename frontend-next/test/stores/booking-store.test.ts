import { describe, expect, it } from "vitest";

import { createBookingStore } from "@/stores/booking-store";
import type { GameSeat } from "@/types/game";
import type { ReservationResponse } from "@/types/reservation";

const seat: GameSeat = {
  gameSeatId: 1,
  zoneId: 10,
  zoneName: "1루 내야석",
  grade: "INFIELD",
  seatBlock: "A",
  seatRow: "3",
  seatNumber: "12",
  price: 18_000,
  status: "AVAILABLE",
};

const reservation: ReservationResponse = {
  reservationId: 100,
  reservationNo: "R-100",
  status: "HOLDING",
  gameSeats: [
    {
      gameSeatId: seat.gameSeatId,
      status: "HELD",
      price: seat.price,
    },
  ],
  holdExpiresAt: "2026-08-06 19:10:00",
  gameAt: "2026-08-10 18:30:00",
};

describe("bookingStore", () => {
  it("같은 좌석을 다시 선택하면 선택 목록에서 해제한다", () => {
    const store = createBookingStore();

    // 첫 호출은 좌석을 추가하고, 같은 ID의 두 번째 호출은 다시 제거해야 한다.
    store.getState().toggleSeat(seat);
    expect(store.getState().selectedSeats).toEqual([seat]);

    store.getState().toggleSeat(seat);
    expect(store.getState().selectedSeats).toEqual([]);
  });

  it("예매 진행에 필요한 식별자와 응답 snapshot을 기록한다", () => {
    const store = createBookingStore();

    store.getState().setGame(1);
    store.getState().setZone(10);
    store.getState().toggleSeat(seat);
    store.getState().setReservation(reservation);
    store.getState().setOrderId(200);
    store.getState().setPaymentId(300);
    store.getState().setQueueExpiry("2026-08-06 19:00:00");

    expect(store.getState()).toMatchObject({
      selectedGameId: 1,
      selectedZoneId: 10,
      selectedSeats: [seat],
      reservation,
      orderId: 200,
      paymentId: 300,
      queueTokenExpiresAt: "2026-08-06 19:00:00",
    });
  });

  it("reset하면 새로운 예매를 시작할 수 있도록 모든 진행 상태를 초기화한다", () => {
    const store = createBookingStore();

    // 다른 action 구현 여부와 무관하게 reset 자체를 검증하도록 진행 상태를 직접 채운다.
    store.setState({
      selectedGameId: 1,
      selectedZoneId: 10,
      selectedSeats: [seat],
      reservation,
      orderId: 200,
      paymentId: 300,
      queueTokenExpiresAt: "2026-08-06 19:00:00",
    });

    store.getState().reset();

    expect(store.getState()).toMatchObject({
      selectedGameId: null,
      selectedZoneId: null,
      selectedSeats: [],
      reservation: null,
      orderId: null,
      paymentId: null,
      queueTokenExpiresAt: null,
    });
  });
});
