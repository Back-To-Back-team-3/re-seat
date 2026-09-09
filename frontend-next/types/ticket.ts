export type TicketStatus =
    | "ISSUED"
    | "REFUND_PENDING"
    | "REFUND_FAILED"
    | "REFUNDED"
    | "USED_ENTERED"
    | "USED_NO_SHOW"
    | "USED"
    | "CANCELED";

export type TicketSummary = {
    ticketId: number;
    ticketNo: string;
    gameId: number;
    seat: string;
    status: TicketStatus;
    qrToken: string;
    gameAt: string;
    refundable?: boolean;
    refundDeadline?: string | null;
};
