import type {ComponentProps, MouseEvent} from "react";

import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {formatGameDate} from "@/lib/date";
import type {TicketStatus, TicketSummary} from "@/types/ticket";

const STATUS_BADGE_VARIANTS: Record<
    TicketStatus,
    ComponentProps<typeof Badge>["variant"]
> = {
    ISSUED: "success",
    REFUND_PENDING: "warning",
    REFUND_FAILED: "destructive",
    REFUNDED: "secondary",
    USED_ENTERED: "secondary",
    USED_NO_SHOW: "secondary",
};

interface TicketCardProps {
    ticket: TicketSummary;
    gameTitle: string;
    showRefundAction: boolean;
    onRequestRefund: (
        ticket: TicketSummary,
        trigger: HTMLButtonElement,
    ) => void;
}

/** 발급된 티켓 한 장의 경기 정보와 현재 환불 상태를 표시한다. */
export function TicketCard({
                               ticket,
                               gameTitle,
                               showRefundAction,
                               onRequestRefund,
                           }: TicketCardProps) {
    const isRefundable =
        ticket.status === "ISSUED" && ticket.refundable !== false;

    const requestRefund = (event: MouseEvent<HTMLButtonElement>) => {
        onRequestRefund(ticket, event.currentTarget);
    };

    return (
        <article className="grid grid-cols-[112px_1fr_130px] overflow-hidden rounded-[13px] border border-border bg-surface shadow-sm max-[720px]:grid-cols-[80px_1fr] max-[560px]:grid-cols-1">
            <div className="grid place-items-center content-center gap-[7px] bg-foreground p-4 text-surface max-[560px]:py-3">
                <span className="font-brand text-xl font-black">
                    Re:<b className="text-brand">Seat</b>
                </span>
                <small className="font-mono text-xs tracking-[1.5px]">
                    ADMIT ONE
                </small>
            </div>
            <div className="grid content-center gap-[5px] p-[22px]">
                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={STATUS_BADGE_VARIANTS[ticket.status]}>
                        {ticket.status}
                    </Badge>
                    {ticket.refundDeadline && ticket.status === "ISSUED" && (
                        <span className="text-[11px] text-muted-foreground">
                            (취소 마감: {formatGameDate(ticket.refundDeadline)})
                        </span>
                    )}
                </div>
                <h2 className="mt-[3px] mb-0 text-[21px]">{gameTitle}</h2>
                <p className="m-0 text-[10px] text-muted-foreground">{ticket.seat}</p>
                <small className="text-xs text-muted-foreground">
                    {formatGameDate(ticket.gameAt)}
                </small>
                <strong className="mt-1 font-mono text-xs">{ticket.ticketNo}</strong>

                {showRefundAction && (
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-border/50 pt-2.5">
                        <span className="text-[11px] text-muted-foreground">
                            * 경기 시작 24시간 전까지 전액 환불
                        </span>
                        {isRefundable ? (
                            <Button onClick={requestRefund} size="sm" variant="destructive">
                                환불 요청
                            </Button>
                        ) : ticket.status === "REFUND_PENDING" ? (
                            <span className="text-xs font-semibold text-warning-foreground">
                                환불 처리 진행 중
                            </span>
                        ) : ticket.status === "REFUNDED" ? (
                            <span className="text-xs text-muted-foreground">환불 완료</span>
                        ) : ticket.status === "REFUND_FAILED" ? (
                            <Button onClick={requestRefund} size="sm" variant="destructive">
                                환불 재시도
                            </Button>
                        ) : (
                            <span className="text-xs text-muted-foreground">환불 불가</span>
                        )}
                    </div>
                )}
            </div>
            <div className="grid place-items-center content-center gap-2 border-l border-dashed border-border max-[720px]:col-span-2 max-[720px]:min-h-[100px] max-[720px]:border-t max-[720px]:border-l-0 max-[560px]:col-span-1">
                <span className="grid size-[65px] place-items-center border-[7px] border-double border-foreground font-mono text-[11px] font-black">
                    QR
                </span>
                <small className="max-w-[90px] overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs text-muted-foreground">
                    {ticket.qrToken}
                </small>
            </div>
        </article>
    );
}
