export type ApiResponse<T> = {
  success: boolean;
  errorCode?: string | null;
  message: string;
  data: T;
};

export type TokenResponse = {
  grantType?: string;
  accessToken: string;
  refreshToken: string;
};

export type UserRole = "USER" | "ADMIN";

export type GameSummary = {
  gameId: number;
  title: string;
  homeTeam?: { teamId: number; name: string };
  awayTeam?: { teamId: number; name: string };
  stadium?: { stadiumId: number; name: string };
  gameAt: string;
  bookingOpenAt?: string;
  bookingCloseAt?: string;
  bookingStatus: "SCHEDULED" | "OPEN" | "CLOSED" | "CANCELLED";
};

export type PageResponse<T> = {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  page?: number;
  size?: number;
};

export type QueueEnterResponse = {
  gameId: number;
  rank: number;
  estimatedWaitSeconds: number | null;
  queueStatus: "WAITING" | "ADMITTED" | "CANCELED";
  admitted: boolean;
  queueToken: string | null;
  tokenExpiresAt: string | null;
};

export type QueueStatusResponse = Pick<
  QueueEnterResponse,
  "rank" | "estimatedWaitSeconds" | "queueStatus" | "admitted"
>;

export type QueueAdmitEvent = {
  admitted: true;
  queueToken: string;
  tokenExpiresAt: string;
};

export type GameSeatStatus = "AVAILABLE" | "HELD" | "SOLD" | "BLOCKED";

export type GameSeat = {
  gameSeatId: number;
  zoneId: number;
  zoneName: string;
  grade: "INFIELD" | "OUTFIELD";
  seatBlock: string;
  seatRow: string;
  seatNumber: string;
  price: number;
  status: GameSeatStatus;
};

export type GameZone = {
  zoneId: number;
  zoneName: string;
  grade: "INFIELD" | "OUTFIELD";
  basePrice: number;
  totalCount: number;
  availableCount: number;
};

export type ReservationResponse = {
  reservationId: number;
  reservationNo: string;
  status: "HOLDING" | "CONFIRMED" | "CANCELED" | "EXPIRED";
  gameSeats: Array<{
    gameSeatId: number;
    status: GameSeatStatus;
    price: number;
  }>;
  holdExpiresAt: string;
  gameAt?: string;
};

export type HoldTimeResponse = {
  reservationId: number;
  remainingSeconds: number;
  status: ReservationResponse["status"];
  expiresAt: string;
};

export type OrderResponse = {
  orderId: number;
  orderNo: string;
  totalAmount: number;
  status: "CREATED" | "PAID" | "CANCELED" | "EXPIRED";
  paymentDeadline: string;
  orderItems: Array<{
    orderItemId: number;
    gameSeatId: number;
    price: number;
  }>;
};

export type PaymentCreateResponse = {
  paymentId: number;
  paymentNo?: string;
  orderId: number;
  amount: number;
  method: "MOCK" | "CARD" | "KAKAO_PAY" | "NAVER_PAY" | "TOSS_PAY";
  status: "READY" | "APPROVED" | "FAILED" | "CANCELED";
  pgProvider: "MOCK" | "TOSS";
  pgOrderId: string;
};

export type TicketSummary = {
  ticketId: number;
  ticketNo: string;
  gameId: number;
  title: string;
  seat: string;
  status: "ISSUED" | "USED" | "CANCELED";
  qrToken: string;
  gameAt: string;
};

export type ApiResult<T> = {
  data: T;
  source: "api" | "mock";
  message?: string;
};
