import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse, PageResponse} from "@/types/api";
import type {TicketStatus, TicketSummary} from "@/types/ticket";

async function getTicketPage(page: number) {
    const response = await apiRequest<ApiResponse<PageResponse<TicketSummary>>>(
        `/tickets?page=${page}&size=100`,
    );
    return unwrap(response);
}

/** 모든 티켓 페이지를 병합해 최근 경기 티켓부터 반환합니다. */
export async function getTickets() {
    const firstPage = await getTicketPage(0);
    const remainingPages = await Promise.all(
        Array.from(
            {length: Math.max(0, firstPage.totalPages - 1)},
            (_, index) => getTicketPage(index + 1),
        ),
    );

    return [firstPage, ...remainingPages]
        .flatMap((page) => page.content)
        .sort(
            (left, right) =>
                right.gameAt.localeCompare(left.gameAt) ||
                right.ticketId - left.ticketId,
        );
}

/**
 * 티켓 1장의 취소(환불)를 접수합니다.
 *
 * @param ticketId 취소할 티켓 식별자
 * @returns 취소 접수 결과
 */
export async function cancelTicket(ticketId: number) {
    const response = await apiRequest<
        ApiResponse<{
            ticketId: number;
            ticketStatus: TicketStatus;
            refundRequestedAt: string;
        }>
    >(`/tickets/${ticketId}/cancel`, {
        method: "POST",
    });
    return unwrap(response);
}
