"use client";

import {useQuery} from "@tanstack/react-query";
import {useParams, useRouter} from "next/navigation";
import {useState} from "react";

import {getGame} from "@/api/games";
import {gameKeys} from "@/api/query-keys/games";
import {Alert} from "@/components/common/alert";
import {SeatMap} from "@/components/seats/seat-map";
import {SeatSummary} from "@/components/seats/seat-summary";
import {useReservation} from "@/hooks/use-reservation";
import {useSeats} from "@/hooks/use-seats";
import {formatGameDate, formatShortDate, parseApiDateTime} from "@/lib/date";
import {STADIUM_IMAGE_URL} from "@/lib/constants";
import {formatPrice} from "@/lib/currency";
import {useBookingStore} from "@/providers/booking-store-provider";

function isDeadlineExpired(target: string | null) {
    const targetDate = parseApiDateTime(target);
    if (!targetDate) return false;
    return Math.max(0, Math.ceil((targetDate.getTime() - Date.now()) / 1000)) === 0;
}

export default function SeatsPage() {
    const params = useParams<{ gameId: string }>();
    const router = useRouter();
    const gameId = Number(params.gameId);

    const zoneId = useBookingStore((state) => state.selectedZoneId);
    const setZone = useBookingStore((state) => state.setZone);
    const toggleSeat = useBookingStore((state) => state.toggleSeat);
    const selectedSeats = useBookingStore((state) => state.selectedSeats);
    const queueTokenExpiresAt = useBookingStore(
        (state) => state.queueTokenExpiresAt,
    );

    const game = useQuery({
        queryKey: gameKeys.detail(gameId),
        queryFn: () => getGame(gameId),
        enabled: Number.isFinite(gameId),
    });
    const reservation = useReservation(gameId);
    const {zones, seats, activeZoneId} = useSeats(gameId, zoneId);

    const selectedZone =
        zones.data?.find((zone) => zone.zoneId === activeZoneId) ?? null;
    const busy = reservation.create.isPending || reservation.cancel.isPending;

    // Vite SeatScreen과 같은 타깃 우선순위: 예약이 있으면 선점 만료 시각, 없으면
    // 입장 토큰 만료 시각을 쓴다(주문 화면이 아니므로 결제 마감 시각은 없다).
    const timerTarget =
        reservation.reservation?.holdExpiresAt ?? queueTokenExpiresAt ?? null;

    // effect에서 setState를 하는 대신, target이 바뀐 렌더에서 바로 상태를
    // 다시 계산한다(React가 권장하는 "렌더 중 상태 조정" 패턴). 이렇게 하면
    // react-hooks/set-state-in-effect 위반 없이 대기열 → 선점 타이머 전환마다
    // 만료 여부가 새 target 기준으로 정확히 재설정된다.
    const [resolvedTarget, setResolvedTarget] = useState(timerTarget);
    const [timerExpired, setTimerExpired] = useState(() =>
        isDeadlineExpired(timerTarget),
    );
    if (timerTarget !== resolvedTarget) {
        setResolvedTarget(timerTarget);
        setTimerExpired(isDeadlineExpired(timerTarget));
    }

    const selectionLocked =
        busy ||
        Boolean(reservation.reservation) ||
        timerExpired ||
        !queueTokenExpiresAt;

    // 예약 생성·취소 실패는 화면에 남는 단서가 없으면 버튼이 그냥 반응하지 않는
    // 것처럼 보인다. 기존 화면과 같은 위치에 원인을 표시한다.
    const requestError =
        reservation.create.error?.message ?? reservation.cancel.error?.message;
    // 선점을 해제해도 이미 사용한 입장 토큰은 돌아오지 않는다. 기존 화면과 같은
    // 문구로 다시 예매해야 한다는 사실을 알린다.
    const cancelNotice = reservation.cancel.isSuccess
        ? "좌석 선점을 해제했습니다. 현재 입장 토큰은 사용되어 새 선점은 다시 예매해야 합니다."
        : null;

    return (
        <section className="mx-auto w-[min(1320px,100%)]">
            {requestError ? (
                <Alert message={requestError} variant="error"/>
            ) : (
                cancelNotice && <Alert message={cancelNotice} variant="success"/>
            )}
            {game.data && (
                <div
                    className="mb-6 flex min-h-[72px] items-center gap-3.5 rounded-[10px] border border-border bg-surface px-[18px] py-3 max-sm:items-start">
          <span
              className="grid size-[42px] place-items-center rounded-lg bg-foreground font-mono font-black text-surface">
            {formatShortDate(game.data.gameAt).day}
          </span>
                    <div className="grid gap-[3px]">
                        <strong className="text-[17px]">
                            {game.data.homeTeam.name}{" "}
                            <em className="mx-1.5 font-mono text-[9px] not-italic text-brand">
                                VS
                            </em>{" "}
                            {game.data.awayTeam.name}
                        </strong>
                        <small className="text-xs text-muted-foreground">
                            {formatGameDate(game.data.gameAt)} · {game.data.stadium.name}
                        </small>
                    </div>
                    <div className="ml-auto flex items-center gap-[7px] text-xs text-muted-foreground max-sm:hidden">
                        <span className="ml-2 size-[11px] rounded-[3px] border border-border bg-surface"/>
                        <span>선택 가능</span>
                        <span className="ml-2 size-[11px] rounded-[3px] bg-brand"/>
                        <span>선택 좌석</span>
                        <span className="ml-2 size-[11px] rounded-[3px] bg-[#d9dce4]"/>
                        <span>선택 불가</span>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-[minmax(0,1fr)_310px] items-start gap-[14px] max-[1024px]:grid-cols-1">
                <div
                    className="col-start-1 row-start-1 grid grid-cols-[minmax(240px,0.7fr)_minmax(0,1.3fr)] overflow-hidden rounded-panel border border-border bg-surface max-[1024px]:col-start-auto max-[1024px]:row-start-auto max-[640px]:grid-cols-1">
                    <div
                        className="col-span-2 flex items-center gap-2.5 border-b border-border px-[18px] py-[17px] max-[640px]:col-span-1">
            <span className="text-xs font-black tracking-[0.1em] text-brand">
              01
            </span>
                        <div className="grid gap-0.5">
                            <strong className="text-[13px]">구역 선택</strong>
                            <small className="text-xs text-muted-foreground">
                                원하는 구역을 먼저 선택하세요.
                            </small>
                        </div>
                    </div>

                    <div
                        className="relative m-[14px] min-h-[220px] overflow-hidden rounded-[9px] bg-surface-soft max-[640px]:h-[155px] max-[640px]:min-h-[155px]">
                        <img
                            alt="잠실야구장 경기 전경"
                            className="size-full object-cover opacity-[0.82]"
                            src={STADIUM_IMAGE_URL}
                        />
                        <span
                            className="absolute bottom-2 left-1/2 -translate-x-1/2 font-mono text-[7px] tracking-[2px] text-muted-foreground">
              JAMSIL STADIUM
            </span>
                    </div>

                    <div
                        className="grid max-h-[470px] grid-cols-2 content-start gap-1.5 overflow-y-auto px-[14px] pb-[14px] max-[900px]:max-h-[280px] max-[900px]:pt-[14px] max-[640px]:grid-cols-1">
                        {zones.data?.map((zone) => (
                            <button
                                className={`flex w-full items-center justify-between gap-2.5 rounded-lg border px-3 py-3 text-left ${
                                    activeZoneId === zone.zoneId
                                        ? "border-brand bg-brand/5"
                                        : "border-border bg-surface"
                                }`}
                                disabled={selectionLocked}
                                key={zone.zoneId}
                                onClick={() => setZone(zone.zoneId)}
                                type="button"
                            >
                                <div className="grid min-w-0 gap-[3px]">
                                    <strong className="truncate text-[11px]">
                                        {zone.zoneName}
                                    </strong>
                                    <small className="text-xs text-muted-foreground">
                                        {zone.grade === "INFIELD" ? "내야" : "외야"} ·{" "}
                                        {formatPrice(zone.basePrice)}
                                    </small>
                                </div>
                                <span className="whitespace-nowrap font-mono text-xs font-black text-brand">
                  {zone.availableCount}
                                    <small className="text-xs text-muted-foreground">
                    {" "}
                                        / {zone.totalCount}석
                  </small>
                </span>
                            </button>
                        ))}
                    </div>
                </div>

                <SeatMap
                    locked={selectionLocked}
                    onToggle={toggleSeat}
                    selectedSeats={selectedSeats}
                    selectedZoneName={selectedZone?.zoneName ?? null}
                    seats={seats.data ?? []}
                />

                <SeatSummary
                    busy={busy}
                    locked={selectionLocked}
                    onCancelReservation={() => reservation.cancel.mutate()}
                    onContinue={() => router.push("/checkout")}
                    onReserve={() => reservation.create.mutate()}
                    onTimerExpire={() => setTimerExpired(true)}
                    reservation={reservation.reservation}
                    seats={selectedSeats}
                    timerExpired={timerExpired}
                    timerTarget={timerTarget}
                />
            </div>
        </section>
    );
}
