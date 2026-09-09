"use client";

import {RefreshCw, TicketX} from "lucide-react";
import {type ComponentProps, useRef, useState} from "react";

import {EmptyState} from "@/components/common/empty-state";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {formatGameDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";
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
    USED: "secondary",
    CANCELED: "destructive",
};

export function TicketList({
    tickets,
    games,
    reloading,
    onReload,
    onCancelTicket,
    onRetryCancelTicket,
    isCanceling = false,
    isRetryingCancel = false,
}: {
    tickets: TicketSummary[];
    games: GameSummary[];
    reloading: boolean;
    onReload: () => void;
    onCancelTicket?: (ticketId: number) => Promise<void> | void;
    onRetryCancelTicket?: (ticketId: number) => Promise<void> | void;
    isCanceling?: boolean;
    isRetryingCancel?: boolean;
}) {
    const [ticketToRefund, setTicketToRefund] = useState<TicketSummary | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const isActionPending = isCanceling || isRetryingCancel;

    const triggerRef = useRef<HTMLButtonElement | null>(null);

    const closeModal = () => {
        setTicketToRefund(null);
        setErrorMessage(null);
        triggerRef.current?.focus();
    };

    const handleConfirmRefund = async () => {
        if (!ticketToRefund) return;
        const handler =
            ticketToRefund.status === "REFUND_FAILED"
                ? (onRetryCancelTicket ?? onCancelTicket)
                : onCancelTicket;
        if (!handler) return;

        try {
            setErrorMessage(null);
            await handler(ticketToRefund.ticketId);
            setTicketToRefund(null);
            triggerRef.current?.focus();
        } catch (error: unknown) {
            if (error instanceof Error) {
                setErrorMessage(error.message);
            } else {
                setErrorMessage("환불 처리 중 오류가 발생했습니다.");
            }
        }
    };

    return (
        <section className="mx-auto w-full max-w-[1120px]">
            <div className="mb-[30px] flex items-end justify-between gap-6 max-[640px]:flex-col max-[640px]:items-start">
                <div>
                    <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
                        MY TICKETS
                    </span>
                    <h1 className="mt-[7px] mb-1.5 text-[clamp(32px,3.5vw,46px)] tracking-[-0.04em]">
                        내 티켓
                    </h1>
                    <p className="m-0 text-sm text-muted-foreground">
                        결제 완료 후 발급된 모바일 티켓을 확인하고 취소(환불)를 요청할 수 있습니다.
                    </p>
                </div>
                <Button
                    disabled={reloading}
                    loading={reloading}
                    onClick={onReload}
                    variant="outline"
                >
                    {!reloading && <RefreshCw aria-hidden="true"/>}
                    티켓 새로고침
                </Button>
            </div>

            {tickets.length === 0 ? (
                <EmptyState
                    description="경기 예매와 결제를 완료하면 이곳에 티켓이 표시됩니다."
                    title="보유한 티켓이 없습니다."
                />
            ) : (
                <div className="grid gap-3.5">
                    {tickets.map((ticket) => {
                        const gameTitle =
                            games.find((game) => game.gameId === ticket.gameId)?.title ??
                            `경기 #${ticket.gameId}`;
                        const isRefundable =
                            ticket.status === "ISSUED" && ticket.refundable !== false;

                        return (
                            <article
                                className="grid grid-cols-[112px_1fr_130px] overflow-hidden rounded-[13px] border border-border bg-surface shadow-sm max-[720px]:grid-cols-[80px_1fr] max-[560px]:grid-cols-1"
                                key={ticket.ticketId}
                            >
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
                                    <p className="m-0 text-[10px] text-muted-foreground">
                                        {ticket.seat}
                                    </p>
                                    <small className="text-xs text-muted-foreground">
                                        {formatGameDate(ticket.gameAt)}
                                    </small>
                                    <strong className="mt-1 font-mono text-xs">
                                        {ticket.ticketNo}
                                    </strong>

                                    {/* 환불 버튼 바 (환불/재시도 콜백이 전달된 경우) */}
                                    {(onCancelTicket || onRetryCancelTicket) && (
                                        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-border/50 pt-2.5">
                                            <span className="text-[11px] text-muted-foreground">
                                                * 경기 시작 24시간 전까지 전액 환불
                                            </span>
                                            {isRefundable ? (
                                                <Button
                                                    onClick={(event) => {
                                                        triggerRef.current = event.currentTarget;
                                                        setErrorMessage(null);
                                                        setTicketToRefund(ticket);
                                                    }}
                                                    size="sm"
                                                    variant="destructive"
                                                >
                                                    환불 요청
                                                </Button>
                                            ) : ticket.status === "REFUND_PENDING" ? (
                                                <span className="text-xs font-semibold text-warning-foreground">
                                                    환불 처리 진행 중
                                                </span>
                                            ) : ticket.status === "REFUNDED" ? (
                                                <span className="text-xs text-muted-foreground">
                                                    환불 완료
                                                </span>
                                            ) : ticket.status === "REFUND_FAILED" ? (
                                                <Button
                                                    onClick={(event) => {
                                                        triggerRef.current = event.currentTarget;
                                                        setErrorMessage(null);
                                                        setTicketToRefund(ticket);
                                                    }}
                                                    size="sm"
                                                    variant="destructive"
                                                >
                                                    환불 재시도
                                                </Button>
                                            ) : (
                                                <span className="text-xs text-muted-foreground">
                                                    환불 불가
                                                </span>
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
                    })}
                </div>
            )}

            <Dialog
                onOpenChange={(open) => {
                    if (!open && !isActionPending) closeModal();
                }}
                open={ticketToRefund !== null}
            >
                {ticketToRefund && (
                    <DialogContent showCloseButton={false}>
                        <DialogHeader>
                            <TicketX aria-hidden="true" className="size-7 text-brand"/>
                            <DialogTitle>
                                {ticketToRefund.status === "REFUND_FAILED"
                                    ? "티켓 환불 재시도 요청"
                                    : "티켓 환불(취소) 요청"}
                            </DialogTitle>
                            <DialogDescription>
                                {ticketToRefund.status === "REFUND_FAILED"
                                    ? "환불 처리에 실패한 티켓입니다. 환불을 다시 요청하시겠습니까?"
                                    : "다음 티켓의 예매를 취소하고 환불을 요청하시겠습니까?"}
                            </DialogDescription>
                        </DialogHeader>
                        <div className="rounded-control border border-border bg-surface-soft p-3 text-xs leading-relaxed text-foreground">
                            <div><strong>좌석:</strong> {ticketToRefund.seat}</div>
                            <div><strong>티켓 번호:</strong> {ticketToRefund.ticketNo}</div>
                            <div><strong>경기 일시:</strong> {formatGameDate(ticketToRefund.gameAt)}</div>
                        </div>
                        <p className="text-xs text-muted-foreground">
                            환불이 정상 처리되면 예약 좌석이 반환되며, 결제 수단에 따라 1~3 영업일 내 환불 처리됩니다.
                        </p>
                        {errorMessage && (
                            <p className="text-xs font-bold text-destructive">
                                {errorMessage}
                            </p>
                        )}
                        <DialogFooter>
                            <Button
                                disabled={isActionPending}
                                onClick={closeModal}
                                variant="outline"
                            >
                                닫기
                            </Button>
                            <Button
                                loading={isActionPending}
                                onClick={handleConfirmRefund}
                                variant="destructive"
                            >
                                {isActionPending
                                    ? ticketToRefund.status === "REFUND_FAILED"
                                        ? "재시도 접수 중..."
                                        : "환불 접수 중..."
                                    : ticketToRefund.status === "REFUND_FAILED"
                                      ? "환불 재시도"
                                      : "환불 확인"}
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                )}
            </Dialog>
        </section>
    );
}
