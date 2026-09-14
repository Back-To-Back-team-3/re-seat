"use client";

import {RefreshCw} from "lucide-react";
import {useState} from "react";

import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminQueue} from "@/hooks/use-admin-queue";
import {getKstDateKey} from "@/lib/date";
import type {AdmissionMetricPeriod} from "@/types/admin";

const periodLabels: Record<AdmissionMetricPeriod, string> = {
    DAILY: "일별",
    WEEKLY: "주별",
    MONTHLY: "월별",
};

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminQueueOverview({gameId}: {gameId: number}) {
    const today = getKstDateKey();
    const initialFrom = getKstDateKey(new Date(Date.now() - 6 * 24 * 60 * 60 * 1000));
    const [period, setPeriod] = useState<AdmissionMetricPeriod>("DAILY");
    const [from, setFrom] = useState(initialFrom);
    const [to, setTo] = useState(today);
    const queue = useAdminQueue(gameId, period, from, to);
    const maxCount = Math.max(
        1,
        ...(queue.metrics?.series.map((item) => item.admittedCount) ?? []),
    );

    return (
        <section className="grid gap-4 rounded-control border border-border bg-muted/20 p-5">
            <header className="flex flex-wrap items-end justify-between gap-3">
                <div>
                    <h3 className="font-black">대기열 현황</h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                        현재 대기 인원과 Queue-Token 발급 지표를 확인합니다.
                    </p>
                </div>
                <Button
                    loading={queue.isRefreshing}
                    onClick={() => void queue.refresh()}
                    size="sm"
                    type="button"
                    variant="outline"
                >
                    <RefreshCw aria-hidden="true"/>
                    새로고침
                </Button>
            </header>

            {queue.error && <Alert message={queue.error.message} variant="error"/>}
            {queue.overview && (
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                    {[
                        ["현재 대기", queue.overview.waitingCount],
                        ["사용 가능 토큰", queue.overview.usableAdmissionCount],
                        ["오늘 입장 허용", queue.overview.admittedToday],
                        ["예매 상태", queue.overview.bookingStatus],
                    ].map(([label, value]) => (
                        <div className="rounded-control border border-border bg-surface p-3" key={label}>
                            <p className="text-xs text-muted-foreground">{label}</p>
                            <strong className="mt-1 block font-mono text-lg">
                                {typeof value === "number" ? value.toLocaleString() : value}
                            </strong>
                        </div>
                    ))}
                </div>
            )}

            <div className="grid gap-3 border-t border-border pt-4 sm:grid-cols-3">
                <label className="grid gap-1 text-xs font-bold">
                    집계 단위
                    <select
                        className={controlClassName}
                        onChange={(event) => setPeriod(event.target.value as AdmissionMetricPeriod)}
                        value={period}
                    >
                        {Object.entries(periodLabels).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                        ))}
                    </select>
                </label>
                <label className="grid gap-1 text-xs font-bold">
                    시작일
                    <input className={controlClassName} max={to} onChange={(event) => setFrom(event.target.value)} type="date" value={from}/>
                </label>
                <label className="grid gap-1 text-xs font-bold">
                    종료일
                    <input className={controlClassName} max={today} min={from} onChange={(event) => setTo(event.target.value)} type="date" value={to}/>
                </label>
            </div>

            {queue.isLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">대기열 지표를 불러오고 있습니다...</p>
            ) : queue.metrics?.series.length ? (
                <div className="grid gap-2">
                    {queue.metrics.series.map((item) => (
                        <div className="grid grid-cols-[90px_minmax(0,1fr)_60px] items-center gap-3 text-xs" key={item.bucket}>
                            <span className="font-mono text-muted-foreground">{item.bucket}</span>
                            <div className="h-2 overflow-hidden rounded-full bg-border">
                                <div
                                    className="h-full rounded-full bg-brand"
                                    style={{width: `${(item.admittedCount / maxCount) * 100}%`}}
                                />
                            </div>
                            <strong className="text-right font-mono">{item.admittedCount.toLocaleString()}명</strong>
                        </div>
                    ))}
                </div>
            ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">조회 기간의 입장 기록이 없습니다.</p>
            )}
        </section>
    );
}
