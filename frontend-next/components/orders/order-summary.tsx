import { STADIUM_IMAGE_URL } from "@/lib/constants";
import { formatPrice } from "@/lib/currency";
import { formatGameDate } from "@/lib/date";
import type { GameSeat, GameSummary } from "@/types/game";

/**
 * 예약 단계(/checkout)와 주문 생성 이후(/orders/[orderId])가 공유하는
 * 경기·좌석·수령 방법 카드. 두 화면 모두 결제 요약(가격·마감·액션 버튼)만
 * 다르고 이 상세 정보는 동일하므로 여기서만 정의한다.
 */
export function OrderSummary({
  game,
  seats,
}: {
  game: GameSummary;
  seats: GameSeat[];
}) {
  return (
    <div className="grid gap-3">
      <article className="grid grid-cols-[150px_1fr] items-center gap-[22px] overflow-hidden rounded-panel border border-border bg-surface max-[640px]:grid-cols-[100px_1fr]">
        <img
          alt="잠실야구장 경기 전경"
          className="h-[145px] w-[150px] object-cover max-[640px]:h-[125px] max-[640px]:w-[100px]"
          src={STADIUM_IMAGE_URL}
        />
        <div className="grid gap-[5px]">
          <span className="font-mono text-xs font-black tracking-[0.1em] text-brand">
            경기 정보
          </span>
          <h2 className="my-1 text-[23px]">
            {game.homeTeam.name}{" "}
            <em className="font-mono text-xs not-italic text-brand">VS</em>{" "}
            {game.awayTeam.name}
          </h2>
          <p className="m-0 text-xs text-muted-foreground">
            {formatGameDate(game.gameAt)}
          </p>
          <small className="text-xs text-muted-foreground">
            {game.stadium.name}
          </small>
        </div>
      </article>

      <article className="rounded-panel border border-border bg-surface p-5">
        <div className="mb-[9px] flex items-center justify-between">
          <h2 className="m-0 text-[15px]">선택 좌석</h2>
          <span className="font-mono text-xs font-black tracking-[0.1em] text-brand">
            {seats.length}석
          </span>
        </div>
        {seats.map((seat) => (
          <div
            className="flex items-center justify-between gap-3 border-b border-border py-2.5 text-[10px]"
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
        ))}
      </article>

      <article className="rounded-panel border border-border bg-surface p-5">
        <div className="mb-[9px] flex items-center justify-between">
          <h2 className="m-0 text-[15px]">수령 방법</h2>
          <span className="font-mono text-xs font-black tracking-[0.1em] text-brand">
            MOBILE
          </span>
        </div>
        <div className="flex items-center gap-3 rounded-lg border border-brand bg-brand/[0.04] p-3.5">
          <span className="text-brand">▣</span>
          <div className="grid flex-1 gap-0.5">
            <strong className="text-[11px]">모바일 티켓</strong>
            <small className="text-xs text-muted-foreground">
              결제 완료 후 내 티켓에서 확인
            </small>
          </div>
          <b className="text-brand">✓</b>
        </div>
      </article>
    </div>
  );
}
