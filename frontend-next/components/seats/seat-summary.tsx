import { Countdown } from "@/components/common/countdown";
import { formatPrice } from "@/lib/currency";
import type { GameSeat } from "@/types/game";
import type { ReservationResponse } from "@/types/reservation";

const PRIMARY_BUTTON_FULL =
  "mx-[14px] mb-2 inline-flex min-h-11 w-[calc(100%-28px)] items-center justify-center gap-3.5 rounded-control border border-brand bg-brand px-[22px] text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(224,53,53,0.2)] transition-colors hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-[0.48]";

const OUTLINE_BUTTON_FULL =
  "mx-[14px] mb-2 inline-flex min-h-11 w-[calc(100%-28px)] items-center justify-center gap-3.5 rounded-control border border-border bg-surface px-[18px] text-[13px] font-extrabold text-foreground transition-colors hover:border-foreground disabled:cursor-not-allowed disabled:opacity-[0.48]";

export function SeatSummary({
  seats,
  busy,
  locked,
  onReserve,
  reservation,
  timerTarget,
  timerExpired = false,
  onTimerExpire,
  onCancelReservation,
  onContinue,
}: {
  seats: GameSeat[];
  busy: boolean;
  locked: boolean;
  onReserve: () => void;
  reservation?: ReservationResponse | null;
  timerTarget?: string | null;
  timerExpired?: boolean;
  onTimerExpire?: () => void;
  onCancelReservation?: () => void;
  onContinue?: () => void;
}) {
  const total = seats.reduce((sum, seat) => sum + seat.price, 0);

  // onContinue가 없으면 좌석 선택 화면 전용 흐름(선점 해제·주문 이동 버튼과
  // 선점 타이머)이 아직 필요 없는 다른 화면(예: 주문 화면 요약)에서 이 컴포넌트를
  // 재사용 중인 것이다. 그 화면은 이 작업 범위 밖이므로 기존 단순 요약을 그대로 둔다.
  if (!onContinue) {
    return (
      <aside className="grid gap-4 rounded-panel border border-border bg-surface p-5 shadow-card">
        <strong>선택 좌석 ({seats.length}/2)</strong>
        {seats.map((seat) => (
          <span className="text-sm" key={seat.gameSeatId}>
            {seat.zoneName} {seat.seatRow}열 {seat.seatNumber}번
          </span>
        ))}
        <strong>{formatPrice(total)}</strong>
        <button
          className="rounded-control bg-brand px-5 py-3 font-bold text-white disabled:bg-muted"
          disabled={busy || locked || seats.length === 0}
          onClick={onReserve}
          type="button"
        >
          {locked ? "예약 완료" : "선택 좌석 예약"}
        </button>
      </aside>
    );
  }

  return (
    <aside className="sticky top-[100px] col-start-2 row-start-1 row-span-2 overflow-hidden rounded-panel border border-border bg-surface max-[1180px]:static max-[1024px]:col-start-auto max-[1024px]:row-start-auto max-[1024px]:row-span-1">
      <div className="flex items-center gap-2.5 border-b border-border px-[18px] py-[17px]">
        <span className="text-xs font-black tracking-[0.1em] text-brand">
          03
        </span>
        <div className="grid gap-0.5">
          <strong className="text-[13px]">선택 확인</strong>
          <small className="text-xs text-muted-foreground">
            최대 2석까지 선택할 수 있습니다.
          </small>
        </div>
      </div>

      {timerTarget ? (
        <div
          className={`m-[14px] flex items-center justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-sm font-bold ${
            timerExpired
              ? "border-brand/40 bg-brand/12"
              : "border-[color-mix(in_srgb,var(--brand)_28%,var(--border))] bg-brand/[0.07]"
          }`}
        >
          <span>
            {reservation ? "좌석 선점 남은 시간" : "좌석 선택 남은 시간"}
          </span>
          <Countdown onExpire={onTimerExpire} target={timerTarget} />
        </div>
      ) : (
        <div className="m-[14px] flex items-center justify-between gap-4 rounded-[10px] border border-brand/40 bg-brand/12 px-4 py-3.5 text-sm font-bold">
          <span>입장 토큰</span>
          <strong>사용 완료</strong>
        </div>
      )}
      {timerExpired && (
        <p className="mx-[14px] mt-[10px] mb-[14px] text-xs font-bold text-brand">
          제한시간이 끝났습니다. 다음 단계로 진행할 수 없습니다.
        </p>
      )}

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
        <strong className="font-mono text-[17px]">{formatPrice(total)}</strong>
      </div>

      {!reservation ? (
        <button
          className={PRIMARY_BUTTON_FULL}
          disabled={seats.length === 0 || locked}
          onClick={onReserve}
          type="button"
        >
          {timerExpired ? "좌석 선택 시간 만료" : `${seats.length}석 선점하기 →`}
        </button>
      ) : (
        <>
          <button
            className={PRIMARY_BUTTON_FULL}
            disabled={timerExpired || busy}
            onClick={onContinue}
            type="button"
          >
            주문 정보 입력 →
          </button>
          <button
            className={OUTLINE_BUTTON_FULL}
            disabled={busy}
            onClick={onCancelReservation}
            type="button"
          >
            선점 해제
          </button>
        </>
      )}

      <small className="block px-[14px] pt-[3px] pb-[18px] text-center text-xs text-muted-foreground">
        입장 후 5분 안에 좌석을 선점해야 하며, 선점 후에는 예약 만료시간이
        적용됩니다.
      </small>
    </aside>
  );
}
