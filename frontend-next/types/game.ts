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
