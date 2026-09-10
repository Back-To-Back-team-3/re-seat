import {TicketX} from "lucide-react";

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
import type {TicketSummary} from "@/types/ticket";

interface TicketRefundDialogProps {
    ticket: TicketSummary | null;
    errorMessage: string | null;
    pending: boolean;
    onClose: () => void;
    onConfirm: () => void;
}

/** 선택한 티켓의 환불 또는 환불 재시도 여부를 확인한다. */
export function TicketRefundDialog({
                                       ticket,
                                       errorMessage,
                                       pending,
                                       onClose,
                                       onConfirm,
                                   }: TicketRefundDialogProps) {
    const retrying = ticket?.status === "REFUND_FAILED";

    return (
        <Dialog
            onOpenChange={(open) => {
                if (!open && !pending) onClose();
            }}
            open={ticket !== null}
        >
            {ticket && (
                <DialogContent showCloseButton={false}>
                    <DialogHeader>
                        <TicketX aria-hidden="true" className="size-7 text-brand"/>
                        <DialogTitle>
                            {retrying
                                ? "티켓 환불 재시도 요청"
                                : "티켓 환불(취소) 요청"}
                        </DialogTitle>
                        <DialogDescription>
                            {retrying
                                ? "환불 처리에 실패한 티켓입니다. 환불을 다시 요청하시겠습니까?"
                                : "다음 티켓의 예매를 취소하고 환불을 요청하시겠습니까?"}
                        </DialogDescription>
                    </DialogHeader>
                    <div className="rounded-control border border-border bg-surface-soft p-3 text-xs leading-relaxed text-foreground">
                        <div><strong>좌석:</strong> {ticket.seat}</div>
                        <div><strong>티켓 번호:</strong> {ticket.ticketNo}</div>
                        <div><strong>경기 일시:</strong> {formatGameDate(ticket.gameAt)}</div>
                    </div>
                    <p className="text-xs text-muted-foreground">
                        환불이 정상 처리되면 예약 좌석이 반환되며, 결제 수단에 따라
                        1~3 영업일 내 환불 처리됩니다.
                    </p>
                    {errorMessage && (
                        <p className="text-xs font-bold text-destructive">
                            {errorMessage}
                        </p>
                    )}
                    <DialogFooter>
                        <Button disabled={pending} onClick={onClose} variant="outline">
                            닫기
                        </Button>
                        <Button
                            loading={pending}
                            onClick={onConfirm}
                            variant="destructive"
                        >
                            {pending
                                ? retrying
                                    ? "재시도 접수 중..."
                                    : "환불 접수 중..."
                                : retrying
                                    ? "환불 재시도"
                                    : "환불 확인"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            )}
        </Dialog>
    );
}
