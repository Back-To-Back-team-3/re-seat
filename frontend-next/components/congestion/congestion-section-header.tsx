import {RefreshCw} from "lucide-react";

import {CongestionBadge} from "@/components/congestion/congestion-badge";
import {Button} from "@/components/ui/button";
import type {StadiumCongestion} from "@/types/congestion";

interface CongestionSectionHeaderProps {
    congestion?: StadiumCongestion;
    loading: boolean;
    onRefresh: () => void;
}

/** 경기장 주변의 전체 혼잡도와 최신 관측 시각을 요약한다. */
export function CongestionSectionHeader({
                                            congestion,
                                            loading,
                                            onRefresh,
                                        }: CongestionSectionHeaderProps) {
    const population =
        congestion?.populationMin != null && congestion?.populationMax != null
            ? `${congestion.populationMin.toLocaleString()} ~ ${congestion.populationMax.toLocaleString()}명`
            : null;

    return (
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border px-6 py-5 max-sm:px-4 max-sm:py-4">
            <div className="grid gap-1">
                <div className="flex flex-wrap items-center gap-2.5">
                    <span className="inline-block size-2 animate-pulse rounded-full bg-brand"/>
                    <h2 className="text-lg font-black tracking-tight text-foreground max-sm:text-base">
                        잠실야구장 주변 실시간 구역별 혼잡도
                    </h2>
                    {congestion && (
                        <CongestionBadge level={congestion.congestionLevel}/>
                    )}
                </div>
                <p className="text-xs text-muted-foreground">
                    서울시 실시간 도시데이터 기반 잠실종합운동장 전체 인구 현황과
                    주요 게이트·지하철역 이동 팁을 안내합니다.
                    {population && (
                        <span className="ml-1 font-mono font-medium text-foreground/90">
                            (실시간 인구: 약 {population})
                        </span>
                    )}
                </p>
            </div>

            <div className="flex items-center gap-3 max-sm:w-full max-sm:justify-between">
                <div className="flex items-center gap-2 rounded-lg border border-border bg-surface-elevated px-3 py-1.5 text-[11px] font-medium text-muted-foreground max-sm:hidden">
                    <span className="flex items-center gap-1">
                        <span className="size-1.5 rounded-full bg-emerald-500"/>
                        여유
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="size-1.5 rounded-full bg-blue-500"/>
                        보통
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="size-1.5 rounded-full bg-amber-500"/>
                        약간 붐빔
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="size-1.5 rounded-full bg-brand"/>
                        붐빔
                    </span>
                </div>

                {congestion?.observedAt && (
                    <span className="font-mono text-xs text-muted-foreground">
                        {congestion.observedAt.slice(11, 16)} 갱신
                    </span>
                )}

                <Button
                    className="bg-surface-elevated"
                    loading={loading}
                    onClick={onRefresh}
                    size="sm"
                    type="button"
                    variant="outline"
                >
                    {!loading && <RefreshCw aria-hidden="true"/>}
                    새로고침
                </Button>
            </div>
        </div>
    );
}
