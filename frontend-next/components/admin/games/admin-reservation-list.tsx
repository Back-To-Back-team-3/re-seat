"use client";

import {ChevronLeft, ChevronRight} from "lucide-react";
import {useState} from "react";

import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminReservations} from "@/hooks/use-admin-reservations";
import {formatPrice} from "@/lib/currency";
import type {ReservationStatus} from "@/types/reservation";

const RESERVATIONS_PER_PAGE = 10;

const statusLabels: Record<ReservationStatus, string> = {
    HOLDING: "선점 중",
    CONFIRMED: "확정",
    CANCELED: "취소",
    EXPIRED: "만료",
};

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminReservationList({gameId}: {gameId: number}) {
    const [status, setStatus] = useState<ReservationStatus | "">("");
    const [page, setPage] = useState(0);
    const reservations = useAdminReservations(
        gameId,
        status || undefined,
        page,
        RESERVATIONS_PER_PAGE,
    );
    const result = reservations.data;

    return (
        <section className="grid gap-4 rounded-control border border-border bg-muted/20 p-5">
            <header className="flex flex-wrap items-end justify-between gap-3">
                <div>
                    <h3 className="font-black">예약·선점 현황</h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                        선택한 경기의 예약 상태와 포함 좌석을 확인합니다.
                    </p>
                </div>
                <label className="grid gap-1 text-xs font-bold">
                    예약 상태
                    <select
                        className={controlClassName}
                        onChange={(event) => {
                            setStatus(event.target.value as ReservationStatus | "");
                            setPage(0);
                        }}
                        value={status}
                    >
                        <option value="">전체</option>
                        {Object.entries(statusLabels).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                        ))}
                    </select>
                </label>
            </header>

            {reservations.error && <Alert message={reservations.error.message} variant="error"/>}
            {reservations.isLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">예약 현황을 불러오고 있습니다...</p>
            ) : result?.content.length ? (
                <div className="overflow-x-auto">
                    <table className="w-full min-w-[720px] border-collapse text-left text-sm">
                        <thead className="border-b border-border text-xs text-muted-foreground">
                            <tr>
                                <th className="px-3 py-2">예약</th>
                                <th className="px-3 py-2">회원</th>
                                <th className="px-3 py-2">상태</th>
                                <th className="px-3 py-2">남은 시간</th>
                                <th className="px-3 py-2">좌석</th>
                            </tr>
                        </thead>
                        <tbody>
                            {result.content.map((reservation) => (
                                <tr className="border-b border-border/70" key={reservation.reservationId}>
                                    <td className="px-3 py-3">
                                        <strong>{reservation.reservationNo}</strong>
                                        <span className="mt-1 block text-xs text-muted-foreground">#{reservation.reservationId}</span>
                                    </td>
                                    <td className="px-3 py-3">#{reservation.userId}</td>
                                    <td className="px-3 py-3">{statusLabels[reservation.status]}</td>
                                    <td className="px-3 py-3">
                                        {reservation.remainingSeconds === null
                                            ? "-"
                                            : `${reservation.remainingSeconds.toLocaleString()}초`}
                                    </td>
                                    <td className="px-3 py-3">
                                        {reservation.seats.map((seat) => (
                                            <span className="block" key={seat.gameSeatId}>
                                                {seat.seat} · {formatPrice(seat.price)}
                                            </span>
                                        ))}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">조건에 맞는 예약이 없습니다.</p>
            )}

            {result && (
                <div className="flex items-center justify-between border-t border-border pt-3 text-sm">
                    <span className="text-muted-foreground">총 {result.totalElements.toLocaleString()}건</span>
                    <nav aria-label="예약 목록 페이지" className="flex items-center gap-2">
                        <Button
                            aria-label="이전 페이지"
                            disabled={result.pageNumber === 0}
                            onClick={() => setPage((value) => Math.max(0, value - 1))}
                            size="icon-sm"
                            type="button"
                            variant="outline"
                        >
                            <ChevronLeft aria-hidden="true"/>
                        </Button>
                        <span>{result.pageNumber + 1} / {Math.max(result.totalPages, 1)} 페이지</span>
                        <Button
                            aria-label="다음 페이지"
                            disabled={result.pageNumber + 1 >= result.totalPages}
                            onClick={() => setPage((value) => Math.min(result.totalPages - 1, value + 1))}
                            size="icon-sm"
                            type="button"
                            variant="outline"
                        >
                            <ChevronRight aria-hidden="true"/>
                        </Button>
                    </nav>
                </div>
            )}
        </section>
    );
}
