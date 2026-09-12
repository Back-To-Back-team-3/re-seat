package com.backtoback.reseat.domain.ticket.admin.dto.response;

public record AdminTicketBulkCancelResult(Long ticketId, boolean success, String message) {

    public static AdminTicketBulkCancelResult success(Long ticketId) {
        return new AdminTicketBulkCancelResult(ticketId, true, "취소 접수 완료");
    }

    public static AdminTicketBulkCancelResult failure(Long ticketId, String message) {
        return new AdminTicketBulkCancelResult(ticketId, false, message);
    }
}
