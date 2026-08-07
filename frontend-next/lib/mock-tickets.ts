import { storage } from "@/lib/storage";
import type { GameSeat, GameSummary } from "@/types/game";
import type { OrderResponse } from "@/types/order";
import type { TicketSummary } from "@/types/ticket";

const MOCK_TICKETS_KEY = "completedMockTickets";

export function getMockTickets() {
  const tickets = storage.session.getJson<TicketSummary[]>(MOCK_TICKETS_KEY);
  if (!Array.isArray(tickets)) return [];

  return tickets.sort(
    (left, right) =>
      right.gameAt.localeCompare(left.gameAt) ||
      right.ticketId - left.ticketId,
  );
}

/**
 * MOCK 결제가 실제 티켓을 만들지 않을 때 주문 항목을 현재 세션용 티켓으로 변환합니다.
 *
 * 음수 ticketId와 MOCK 번호 규칙은 기존 Vite와 동일하며, 좌석 상세를 찾지 못해도
 * orderItem의 gameSeatId를 표시해 결제 완료 결과가 사라지지 않게 합니다.
 */
export function createMockTickets(
  game: GameSummary,
  order: OrderResponse,
  seats: GameSeat[],
): TicketSummary[] {
  return order.orderItems.map((item, index) => {
    const seat = seats.find(
      (candidate) => candidate.gameSeatId === item.gameSeatId,
    );
    return {
      ticketId: -item.orderItemId,
      ticketNo: `MOCK-${order.orderNo}-${index + 1}`,
      gameId: game.gameId,
      seat: seat
        ? `${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번`
        : `좌석 #${item.gameSeatId}`,
      status: "ISSUED",
      qrToken: `MOCK-QR-${order.orderId}-${item.orderItemId}`,
      gameAt: game.gameAt,
    };
  });
}

export function saveMockTickets(tickets: TicketSummary[]) {
  const byNumber = new Map(
    [...getMockTickets(), ...tickets].map((ticket) => [
      ticket.ticketNo,
      ticket,
    ]),
  );
  const stored = [...byNumber.values()].sort(
    (left, right) =>
      right.gameAt.localeCompare(left.gameAt) ||
      right.ticketId - left.ticketId,
  );
  storage.session.set(MOCK_TICKETS_KEY, JSON.stringify(stored));
  return stored;
}
