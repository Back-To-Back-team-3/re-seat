"use client";

import {useMemo} from "react";

import {EmptyState} from "@/components/common/empty-state";
import {formatPrice} from "@/lib/currency";
import type {GameSeat} from "@/types/game";

/**
 * Vite의 seat-picker-panel(App.tsx)을 그대로 옮긴다.
 *
 * 그리드 자리는 페이지의 seat-layout 그리드(왼쪽 열)에 맞춰 2번째 행에 놓고,
 * 1024px 이하에서는 페이지 쪽 그리드가 단일 열로 바뀌므로 자리 지정을 해제해
 * 문서 순서(구역 → 좌석 → 선택 확인)대로 쌓이게 한다.
 */
export function SeatMap({
                            seats,
                            selectedSeats,
                            selectedZoneName,
                            locked,
                            onToggle,
                        }: {
    seats: GameSeat[];
    selectedSeats: GameSeat[];
    selectedZoneName: string | null;
    locked: boolean;
    onToggle: (seat: GameSeat) => void;
}) {
    // 좌석은 열(seatRow) 단위로 묶어 보여준다. 구역이 바뀔 때마다 서버가 준 좌석
    // 순서를 그대로 따르고, 열 이름이나 한 열의 좌석 수를 고정 값으로 가정하지
    // 않는다(Vite App.tsx의 seatRows useMemo와 같은 방식).
    const seatRows = useMemo(() => {
        const rows = new Map<string, GameSeat[]>();
        seats.forEach((seat) => {
            const row = rows.get(seat.seatRow) ?? [];
            row.push(seat);
            rows.set(seat.seatRow, row);
        });
        return Array.from(rows.entries());
    }, [seats]);

    return (
        <div
            className="col-start-1 row-start-2 overflow-hidden rounded-panel border border-border bg-surface max-[1024px]:col-start-auto max-[1024px]:row-start-auto">
            <div className="flex items-center gap-2.5 border-b border-border px-[18px] py-[17px]">
        <span className="text-xs font-black tracking-[0.1em] text-brand">
          02
        </span>
                <div className="grid gap-0.5">
                    <strong className="text-[13px]">좌석 선택</strong>
                    <small className="text-xs text-muted-foreground">
                        {selectedZoneName
                            ? `${selectedZoneName}의 실제 좌석을 선택하세요.`
                            : "구역을 선택해주세요."}
                    </small>
                </div>
            </div>

            {seatRows.length === 0 ? (
                <EmptyState
                    description="다른 구역을 선택하거나 좌석 상태를 새로 확인해주세요."
                    title="표시할 좌석이 없습니다."
                />
            ) : (
                <div className="max-h-[650px] overflow-auto p-5 max-[640px]:px-[10px] max-[640px]:py-4">
                    <div
                        className="mx-auto mb-7 w-[76%] rounded-[50%_50%_7px_7px] bg-surface-soft p-[7px] text-center font-mono text-[7px] tracking-[2px] text-muted-foreground">
                        그라운드 방향
                    </div>
                    {seatRows.map(([rowName, rowSeats]) => (
                        <div
                            className="mb-2 grid grid-cols-[26px_minmax(320px,1fr)] items-center gap-2 max-[640px]:grid-cols-[20px_minmax(300px,1fr)]"
                            key={rowName}
                        >
                            <strong className="text-center font-mono text-xs text-muted-foreground">
                                {rowName}
                            </strong>
                            <div className="grid grid-cols-[repeat(10,minmax(29px,1fr))] gap-1.5 max-[640px]:gap-1">
                                {rowSeats.map((seat) => {
                                    const selected = selectedSeats.some(
                                        (candidate) => candidate.gameSeatId === seat.gameSeatId,
                                    );
                                    const unavailable = seat.status !== "AVAILABLE";

                                    return (
                                        <button
                                            aria-label={`${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번 ${formatPrice(seat.price)}`}
                                            aria-pressed={selected}
                                            className={`aspect-square min-h-[30px] min-w-[30px] rounded-t-[5px] rounded-b-[8px] border font-mono text-[11px] font-extrabold transition-[transform,background-color] duration-[120ms] ${seatToneClass(seat.status, selected)}`}
                                            disabled={
                                                locked ||
                                                unavailable ||
                                                (!selected && selectedSeats.length >= 2)
                                            }
                                            key={seat.gameSeatId}
                                            onClick={() => onToggle(seat)}
                                            title={`${seat.zoneName} ${seat.seatRow}열 ${seat.seatNumber}번 · ${formatPrice(seat.price)}`}
                                            type="button"
                                        >
                                            {seat.seatNumber}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

/**
 * 좌석 상태별 색은 Vite styles.css의 .seat.held/.sold/.blocked가 .seat.picked보다
 * 뒤에 선언되어 있어(동일 우선순위) 상태 색이 선점색보다 우선한다. 이 함수도
 * 같은 우선순위(상태 → 선택 → 기본)로 클래스를 고른다.
 */
function seatToneClass(status: GameSeat["status"], selected: boolean) {
    if (status === "HELD") return "border-border bg-[#f5cf8d] text-[#62420b]";
    if (status === "SOLD") return "border-border bg-[#c7cad3] text-[#6d7280]";
    if (status === "BLOCKED") return "border-border bg-[#353943] text-white";
    if (selected) {
        return "border-brand bg-brand text-white shadow-[0_5px_12px_rgba(224,53,53,0.24)]";
    }
    return "border-border bg-surface text-foreground hover:border-brand hover:-translate-y-0.5";
}
