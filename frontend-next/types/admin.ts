import type {PageResponse} from "@/types/api";
import type {UserRole} from "@/types/auth";
import type {GameSeatStatus, GameSummary} from "@/types/game";
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

export type GameSeatOpenResponse = {
    gameId: number;
    createdCount: number;
    priceRange: {min: number; max: number};
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
