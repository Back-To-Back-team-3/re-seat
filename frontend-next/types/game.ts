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

/**
 * 경기 상세 조회에서만 제공되는 구장 주소와 수용 인원을 포함한 응답입니다.
 * 목록 응답의 구장 요약 타입은 그대로 유지해 두 API의 실제 계약을 구분합니다.
 */
export type GameDetail = Omit<GameSummary, "stadium"> & {
    stadium: GameSummary["stadium"] & {
        address: string;
        totalCapacity: number;
    };
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
