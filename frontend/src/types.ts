export type ApiResponse<T> = {
  success: boolean;
  errorCode: string | null;
  message: string;
  data: T | null;
};

export type TokenResponse = {
  grantType: string;
  accessToken: string;
  refreshToken: string;
};

export type UserRole = "USER" | "ADMIN";

export type UserProfile = {
  id: number;
  email: string;
  name: string | null;
  nickname: string | null;
  phone: string | null;
  isVerified: boolean;
};

export type GameSummary = {
  gameId: number;
  title: string;
  homeTeam: { teamId: number; name: string };
  awayTeam: { teamId: number; name: string };
  stadium: { stadiumId: number; name: string };
  gameAt: string;
  bookingOpenAt: string;
  bookingCloseAt: string;
  bookingStatus: "SCHEDULED" | "OPEN" | "CLOSED" | "CANCELLED";
};

export type PageResponse<T> = {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
};

export type QueueStatusResponse = {
  rank: number;
  estimatedWaitSeconds: number | null;
  queueStatus: "WAITING" | "ADMITTED" | "CANCELED";
  admitted: boolean;
};

export type QueueViewState = QueueStatusResponse & {
  gameId: number;
  registrationPending: boolean;
  queueToken: string | null;
  tokenExpiresAt: string | null;
};

export type QueueAdmitEvent = {
  admitted: true;
  queueToken: string;
  tokenExpiresAt: string;
};

export type QueueCancelResponse = {
  gameId: number;
  queueStatus: "CANCELED";
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

export type ReservationStatus = "HOLDING" | "CONFIRMED" | "CANCELED" | "EXPIRED";

export type ReservationResponse = {
  reservationId: number;
  reservationNo: string;
  status: ReservationStatus;
  gameSeats: Array<{
    gameSeatId: number;
    status: GameSeatStatus;
    price: number;
  }>;
  holdExpiresAt: string;
  gameAt: string;
};

export type HoldTimeResponse = {
  reservationId: number;
  remainingSeconds: number;
  status: ReservationStatus;
  expiresAt: string;
};

export type OrderStatus = "CREATED" | "PAID" | "CANCELED" | "EXPIRED";

export type OrderResponse = {
  orderId: number;
  orderNo: string;
  totalAmount: number;
  status: OrderStatus;
  paymentDeadline: string;
  holdExpiresAt: string;
  orderItems: Array<{
    orderItemId: number;
    gameSeatId: number;
    price: number;
  }>;
};

export type PaymentStatus = "READY" | "APPROVED" | "FAILED" | "CANCELED";

export type PaymentCreateResponse = {
  paymentId: number;
  paymentNo: string;
  orderId: number;
  amount: number;
  method: string | null;
  status: PaymentStatus;
  pgProvider: "MOCK" | "TOSS" | "KAKAO" | "NAVER";
  pgOrderId: string;
};

export type PaymentActionResponse = {
  paymentId: number;
  status: PaymentStatus;
};

export type PaymentResponse = Omit<PaymentCreateResponse, "pgOrderId"> & {
  failReason: string | null;
  approvedAt: string | null;
  failedAt: string | null;
};

export type TicketSummary = {
  ticketId: number;
  ticketNo: string;
  gameId: number;
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
