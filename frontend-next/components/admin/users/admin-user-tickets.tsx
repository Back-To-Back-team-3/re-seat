"use client";

import {useState} from "react";

import {Button} from "@/components/ui/button";
import type {AdminUserTicket} from "@/types/admin";

export function AdminUserTickets({
    tickets,
    onCancel,
    isCanceling,
}: {
    tickets: AdminUserTicket[];
    onCancel: (ticketId: number, reason: string) => Promise<unknown>;
    isCanceling: boolean;
}) {
    const [selectedTicketId, setSelectedTicketId] = useState<number | null>(null);
    const [reason, setReason] = useState("");

    if (tickets.length === 0) {
        return <p className="py-8 text-sm text-muted-foreground">보유 티켓이 없습니다.</p>;
    }

    return (
        <div className="grid gap-3">
            {tickets.map((ticket) => (
                <article className="grid gap-3 border-t border-border pt-4 sm:grid-cols-[1fr_auto] sm:items-center" key={ticket.ticketId}>
                    <div className="min-w-0">
                        <p className="truncate font-bold">{ticket.gameTitle}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                            {ticket.ticketNo} · {ticket.seat} · {ticket.status}
                        </p>
                    </div>
                    {selectedTicketId === ticket.ticketId ? (
                        <div className="flex flex-wrap justify-end gap-2">
                            <label className="sr-only" htmlFor={`cancel-reason-${ticket.ticketId}`}>취소 사유</label>
                            <input
                                className="h-9 min-w-56 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary"
                                id={`cancel-reason-${ticket.ticketId}`}
                                onChange={(event) => setReason(event.target.value)}
                                placeholder="취소 사유"
                                value={reason}
                            />
                            <Button
                                disabled={!reason.trim()}
                                loading={isCanceling}
                                onClick={async () => {
                                    await onCancel(ticket.ticketId, reason.trim());
                                    setSelectedTicketId(null);
                                    setReason("");
                                }}
                                size="sm"
                                type="button"
                                variant="destructive"
                            >
                                취소 확정
                            </Button>
                            <Button onClick={() => setSelectedTicketId(null)} size="sm" type="button" variant="ghost">닫기</Button>
                        </div>
                    ) : (
                        <Button
                            disabled={ticket.status !== "ISSUED"}
                            onClick={() => setSelectedTicketId(ticket.ticketId)}
                            size="sm"
                            type="button"
                            variant="outline"
                        >
                            직권 취소
                        </Button>
                    )}
                </article>
            ))}
        </div>
    );
}
