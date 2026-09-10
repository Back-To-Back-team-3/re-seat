import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse} from "@/types/api";
import type {
    AdminLoginResponse,
    AdminTicketCancelResponse,
    AdminUser,
    AdminUserPage,
    AdminUserSearchCondition,
    AdminUserTicketPage,
    GameBookingStatusResponse,
    GameSeatOpenResponse,
    UserStatus,
} from "@/types/admin";
import type {UserRole} from "@/types/auth";
import type {GameSummary} from "@/types/game";
import type {TicketStatus} from "@/types/ticket";

type AdminUserPayload = Omit<AdminUser, "isVerified"> & {
    isVerified?: boolean;
    verified?: boolean;
};

function normalizeAdminUser({
    isVerified,
    verified,
    ...user
}: AdminUserPayload): AdminUser {
    return {...user, isVerified: isVerified ?? verified ?? false};
}

function appendDefinedParams(
    params: URLSearchParams,
    values: Record<string, string | number | undefined>,
) {
    Object.entries(values).forEach(([key, value]) => {
        if (value !== undefined && value !== "") {
            params.set(key, String(value));
        }
    });
}

export async function loginAdmin(email: string, password: string) {
    const response = await apiRequest<ApiResponse<AdminLoginResponse>>(
        "/auth/admin/login",
        {method: "POST", body: JSON.stringify({email, password})},
    );
    return unwrap(response);
}

export async function searchAdminUsers(
    condition: AdminUserSearchCondition = {},
    page = 0,
    size = 20,
) {
    const params = new URLSearchParams();
    appendDefinedParams(params, {...condition, page, size});
    const response = await apiRequest<
        ApiResponse<Omit<AdminUserPage, "content"> & {content: AdminUserPayload[]}>
    >(
        `/admin/users?${params}`,
    );
    const pageResult = unwrap(response);
    return {
        ...pageResult,
        content: pageResult.content.map(normalizeAdminUser),
    } satisfies AdminUserPage;
}

export async function getAdminUser(userId: number) {
    const response = await apiRequest<ApiResponse<AdminUserPayload>>(
        `/admin/users/${userId}`,
    );
    return normalizeAdminUser(unwrap(response));
}

export async function updateUserRole(userId: number, role: UserRole) {
    await apiRequest<ApiResponse<void>>(`/admin/users/${userId}/role`, {
        method: "PATCH",
        body: JSON.stringify({role}),
    });
}

export async function updateUserStatus(userId: number, status: UserStatus) {
    await apiRequest<ApiResponse<void>>(`/admin/users/${userId}/status`, {
        method: "PATCH",
        body: JSON.stringify({status}),
    });
}

export async function updateGameBookingStatus(
    gameId: number,
    bookingStatus: Exclude<GameSummary["bookingStatus"], "SCHEDULED">,
    reason: string,
) {
    const response = await apiRequest<ApiResponse<GameBookingStatusResponse>>(
        `/admin/games/${gameId}/booking-status`,
        {
            method: "PATCH",
            body: JSON.stringify({bookingStatus, reason}),
        },
    );
    return unwrap(response);
}

export async function openGameSeatInventory(gameId: number) {
    const response = await apiRequest<ApiResponse<GameSeatOpenResponse>>(
        `/admin/games/${gameId}/seats`,
        {method: "POST"},
    );
    return unwrap(response);
}

export async function getAdminUserTickets(
    userId: number,
    status?: TicketStatus,
    page = 0,
    size = 20,
) {
    const params = new URLSearchParams();
    appendDefinedParams(params, {status, page, size});
    const response = await apiRequest<ApiResponse<AdminUserTicketPage>>(
        `/admin/tickets/users/${userId}?${params}`,
    );
    return unwrap(response);
}

export async function cancelAdminTicket(ticketId: number, reason: string) {
    const response = await apiRequest<ApiResponse<AdminTicketCancelResponse>>(
        `/admin/tickets/${ticketId}/cancel`,
        {method: "POST", body: JSON.stringify({reason})},
    );
    return unwrap(response);
}
