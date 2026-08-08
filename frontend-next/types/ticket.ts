export type TicketSummary = {
  ticketId: number;
  ticketNo: string;
  gameId: number;
  seat: string;
  status: "ISSUED" | "USED" | "CANCELED";
  qrToken: string;
  gameAt: string;
};
