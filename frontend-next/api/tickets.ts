import { apiRequest, unwrap } from "@/api/client";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { TicketSummary } from "@/types/ticket";

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
      { length: Math.max(0, firstPage.totalPages - 1) },
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
