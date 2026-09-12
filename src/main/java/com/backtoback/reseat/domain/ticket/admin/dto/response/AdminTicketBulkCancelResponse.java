package com.backtoback.reseat.domain.ticket.admin.dto.response;

import java.util.List;

public record AdminTicketBulkCancelResponse(
    long totalCount,
    long successCount,
    long failureCount,
    List<AdminTicketBulkCancelResult> results
) {

    public static AdminTicketBulkCancelResponse of(List<AdminTicketBulkCancelResult> results) {
        long successCount = results.stream().filter(AdminTicketBulkCancelResult::success).count();

        return new AdminTicketBulkCancelResponse(results.size(), successCount, results.size() - successCount, results);
    }
}
