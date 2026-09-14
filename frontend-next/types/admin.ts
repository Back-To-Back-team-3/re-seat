import type {PageResponse} from "@/types/api";
import type {UserRole} from "@/types/auth";
import type {GameSeatStatus, GameSummary} from "@/types/game";
import type {ReservationStatus} from "@/types/reservation";
import type {TicketStatus} from "@/types/ticket";

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";

export type AdminLoginResponse = {
    grantType: string;
    accessToken: string;
    refreshToken: string;
    userId: number;
    email: string;
    name: string;
    role: "ADMIN";
};

export type AdminUser = {
    id: number;
    email: string;
    name: string | null;
    nickname: string | null;
    phone: string | null;
    role: UserRole;
    status: UserStatus;
    isVerified: boolean;
    createdAt: string;
    updatedAt: string;
};

export type AdminUserSearchCondition = {
    email?: string;
    name?: string;
    nickname?: string;
    phone?: string;
    role?: UserRole;
    status?: UserStatus;
};

export type GameBookingStatusResponse = {
    gameId: number;
    bookingStatus: GameSummary["bookingStatus"];
};

export type AdminGameSearchCondition = {
    homeTeamId?: number;
    awayTeamId?: number;
    stadiumId?: number;
    from?: string;
    to?: string;
    bookingStatus?: GameSummary["bookingStatus"];
};

export type AdminGamePage = PageResponse<GameSummary>;

export type AdminGameRegisterRequest = {
    stadiumId: number;
    homeTeamId: number;
    awayTeamId: number;
    gameAt: string;
    bookingOpenAt: string;
    bookingCloseAt: string;
    title?: string;
};

export type AdminGameRegisterResponse = {
    gameId: number;
    bookingStatus: "SCHEDULED";
};

export type GameSeatOpenResponse = {
    gameId: number;
    createdCount: number;
    priceRange: {min: number; max: number};
};

export type AdminSeatInventorySummary = {
    available: number;
    held: number;
    sold: number;
    blocked: number;
};

export type AdminGameSeatStatusResponse = {
    gameSeatId: number;
    status: GameSeatStatus;
};

export type AdminReservationSeat = {
    gameSeatId: number;
    seat: string;
    price: number;
};

export type AdminReservation = {
    reservationId: number;
    reservationNo: string;
    userId: number;
    status: ReservationStatus;
    remainingSeconds: number | null;
    seats: AdminReservationSeat[];
};

export type AdminReservationPage = PageResponse<AdminReservation>;

export type AdmissionMetricPeriod = "DAILY" | "WEEKLY" | "MONTHLY";

export type AdminQueueOverview = {
    gameId: number;
    bookingStatus: GameSummary["bookingStatus"];
    waitingCount: number;
    usableAdmissionCount: number;
    admittedToday: number;
    collectedAt: string;
};

export type AdminQueueAdmissionMetrics = {
    gameId: number;
    period: AdmissionMetricPeriod;
    from: string;
    to: string;
    series: Array<{
        bucket: string;
        admittedCount: number;
    }>;
};

export type AdminUserTicket = {
    ticketId: number;
    ticketNo: string;
    status: TicketStatus;
    qrToken: string;
    issuedAt: string;
    usedAt: string | null;
    canceledAt: string | null;
    gameId: number;
    gameTitle: string;
    stadiumName: string | null;
    homeTeamName: string | null;
    awayTeamName: string | null;
    gameAt: string;
    seat: string;
    gameSeatId: number;
    zoneName: string | null;
    seatBlock: string;
    seatRow: string;
    seatNumber: string;
};

export type AdminTicketCancelResponse = {
    ticketId: number;
    ticketNo: string;
    status: TicketStatus;
    cancelReason: "ADMIN_FORCE_CANCEL";
    cancelDetail: string;
    canceledAt: string | null;
    gameSeatId: number;
    seatStatus: GameSeatStatus;
};

export type AdminUserPage = PageResponse<AdminUser>;
export type AdminUserTicketPage = PageResponse<AdminUserTicket>;

export type AdminTicketSearchCondition = {
    userId?: number;
    status?: TicketStatus;
    gameDateFrom?: string;
    gameDateTo?: string;
};

export type AdminTicketQrReissueResponse = {
    ticketId: number;
    qrToken: string;
};

export type AdminTicketVerifyResponse = {
    ticketId: number;
    status: TicketStatus;
    usedAt: string;
    seat: string;
    holderName: string | null;
};

export type AdminTicketBulkCancelResponse = {
    totalCount: number;
    successCount: number;
    failureCount: number;
    results: Array<{
        ticketId: number;
        success: boolean;
        message: string;
    }>;
};
