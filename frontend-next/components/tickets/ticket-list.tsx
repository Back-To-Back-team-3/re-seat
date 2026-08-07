import { EmptyState } from "@/components/common/empty-state";
import { formatGameDate } from "@/lib/date";
import type { TicketSummary } from "@/types/ticket";

export function TicketList({
  tickets,
  source,
}: {
  tickets: TicketSummary[];
  source: "api" | "mock";
}) {
  if (tickets.length === 0) {
    return (
      <EmptyState
        description="예매와 결제를 완료하면 발급된 티켓을 확인할 수 있습니다."
        title="발급된 티켓이 없습니다."
      />
    );
  }

  return (
    <section className="grid gap-4">
      {source === "mock" && (
        <p className="rounded-control bg-brand/10 px-4 py-3 text-sm text-brand">
          결제 완료 데이터를 바탕으로 만든 임시 티켓입니다.
        </p>
      )}
      {tickets.map((ticket) => (
        <article
          className="grid gap-3 rounded-panel border border-border bg-surface p-6 shadow-card sm:grid-cols-[1fr_auto]"
          key={ticket.ticketId}
        >
          <div>
            <span className="text-xs font-bold text-brand">
              {ticket.status}
            </span>
            <strong className="mt-2 block text-xl">{ticket.ticketNo}</strong>
            <p className="text-sm text-muted-foreground">
              {formatGameDate(ticket.gameAt)} · {ticket.seat}
            </p>
          </div>
          <code className="self-center rounded-control bg-foreground px-4 py-3 text-xs text-surface">
            {ticket.qrToken}
          </code>
        </article>
      ))}
    </section>
  );
}
