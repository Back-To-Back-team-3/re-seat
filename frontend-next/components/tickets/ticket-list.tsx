"use client";

import {RefreshCw} from "lucide-react";
import {useRef, useState} from "react";

import {EmptyState} from "@/components/common/empty-state";
import {PageIntro} from "@/components/common/page-intro";
import {TicketCard} from "@/components/tickets/ticket-card";
import {TicketRefundDialog} from "@/components/tickets/ticket-refund-dialog";
import {Button} from "@/components/ui/button";
import type {GameSummary} from "@/types/game";
import type {TicketSummary} from "@/types/ticket";

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
                <PageIntro
                    description="결제 완료 후 발급된 모바일 티켓을 확인하고 취소(환불)를 요청할 수 있습니다."
                    eyebrow="MY TICKETS"
                    title="내 티켓"
                />
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
                    {tickets.map((ticket) => (
                        <TicketCard
                            gameTitle={
                                games.find((game) => game.gameId === ticket.gameId)
                                    ?.title ?? `경기 #${ticket.gameId}`
                            }
                            key={ticket.ticketId}
                            onRequestRefund={(selectedTicket, trigger) => {
                                triggerRef.current = trigger;
                                setErrorMessage(null);
                                setTicketToRefund(selectedTicket);
                            }}
                            showRefundAction={Boolean(
                                onCancelTicket || onRetryCancelTicket,
                            )}
                            ticket={ticket}
                        />
                    ))}
                </div>
            )}

            <TicketRefundDialog
                errorMessage={errorMessage}
                onClose={closeModal}
                onConfirm={() => void handleConfirmRefund()}
                pending={isActionPending}
                ticket={ticketToRefund}
            />
        </section>
    );
}
