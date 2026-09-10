import {ArrowRight, RotateCcw} from "lucide-react";

import {BookingPanelHeader} from "@/components/booking/booking-panel-header";
import {DeadlinePanel} from "@/components/booking/deadline-panel";
import {Button} from "@/components/ui/button";
import {formatPrice} from "@/lib/currency";
import type {GameSeat} from "@/types/game";
import type {ReservationResponse} from "@/types/reservation";

const FULL_WIDTH_BUTTON = "mx-[14px] mb-2 w-[calc(100%-28px)] text-[13px]";

type BookingSeatSummaryProps = {
    seats: GameSeat[];
    total: number;
    busy: boolean;
    locked: boolean;
    reservation?: ReservationResponse | null;
    timerTarget?: string | null;
    timerExpired: boolean;
    onTimerExpire?: () => void;
    onCancelReservation?: () => void;
    onContinue: () => void;
    onReserve: () => void;
};

/** 좌석 선택과 선점 단계의 기한, 합계 및 다음 행동을 함께 표시한다. */
export function BookingSeatSummary({
    seats,
    total,
    busy,
    locked,
    reservation,
    timerTarget,
    timerExpired,
    onTimerExpire,
    onCancelReservation,
    onContinue,
    onReserve,
}: BookingSeatSummaryProps) {
    return (
        <aside className="sticky top-[100px] col-start-2 row-start-1 row-span-2 overflow-hidden rounded-panel border border-border bg-surface max-[1180px]:static max-[1024px]:col-start-auto max-[1024px]:row-start-auto max-[1024px]:row-span-1">
            <BookingPanelHeader
                description="최대 2석까지 선택할 수 있습니다."
                step="03"
                title="선택 확인"
            />

            <DeadlinePanel
                className="m-[14px]"
                expired={timerExpired}
                expiredMessage="제한시간이 끝났습니다. 다음 단계로 진행할 수 없습니다."
                fallbackValue="사용 완료"
                label={
                    timerTarget
                        ? reservation
                            ? "좌석 선점 남은 시간"
                            : "좌석 선택 남은 시간"
                        : "입장 토큰"
                }
                messageClassName="mx-[14px] mt-[10px] mb-[14px]"
                onExpire={onTimerExpire}
                target={timerTarget ?? null}
            />

            <div className="grid min-h-[120px] content-start gap-2 p-[14px] max-[1180px]:min-h-[auto] max-[1180px]:grid-cols-2 max-sm:grid-cols-1">
                {seats.length === 0 ? (
                    <p className="my-[34px] self-center text-center text-xs text-muted-foreground">
                        좌석을 선택하면 이곳에 표시됩니다.
                    </p>
                ) : (
                    seats.map((seat) => (
                        <div
                            className="flex items-center justify-between gap-3 border-b border-border py-2.5 text-xs"
                            key={seat.gameSeatId}
                        >
                            <span className="grid gap-0.5">
                                <strong>{seat.zoneName}</strong>
                                <small className="text-xs text-muted-foreground">
                                    {seat.seatRow}열 {seat.seatNumber}번
                                </small>
                            </span>
                            <strong>{formatPrice(seat.price)}</strong>
                        </div>
                    ))
                )}
            </div>

            <div className="mx-[14px] flex items-center justify-between gap-4 border-t border-border py-4">
                <span className="text-xs text-muted-foreground">
                    총 결제 예정 금액
                </span>
                <strong className="font-mono text-[17px]">
                    {formatPrice(total)}
                </strong>
            </div>

            {!reservation ? (
                <Button
                    className={FULL_WIDTH_BUTTON}
                    disabled={seats.length === 0 || locked}
                    onClick={onReserve}
                    type="button"
                >
                    {timerExpired ? "좌석 선택 시간 만료" : `${seats.length}석 선점하기`}
                    {!timerExpired && <ArrowRight aria-hidden="true"/>}
                </Button>
            ) : (
                <>
                    <Button
                        className={FULL_WIDTH_BUTTON}
                        disabled={timerExpired || busy}
                        onClick={onContinue}
                        type="button"
                    >
                        주문 정보 입력
                        <ArrowRight aria-hidden="true"/>
                    </Button>
                    <Button
                        className={FULL_WIDTH_BUTTON}
                        disabled={busy}
                        onClick={onCancelReservation}
                        type="button"
                        variant="outline"
                    >
                        <RotateCcw aria-hidden="true"/>
                        선점 해제
                    </Button>
                </>
            )}

            <small className="block px-[14px] pt-[3px] pb-[18px] text-center text-xs text-muted-foreground">
                입장 후 5분 안에 좌석을 선점해야 하며, 선점 후에는 예약 만료시간이
                적용됩니다.
            </small>
        </aside>
    );
}
