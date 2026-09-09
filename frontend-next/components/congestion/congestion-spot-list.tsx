import {useMemo, useState} from "react";

import {CongestionBadge} from "@/components/congestion/congestion-badge";
import {Button} from "@/components/ui/button";
import type {
    CongestionLevel,
    StadiumZoneSpot,
    ZoneCategory,
} from "@/types/congestion";

type SortOption = "default" | "busy" | "free";
type FilterCategory = "ALL" | ZoneCategory;

const CATEGORY_TABS: {id: FilterCategory; label: string}[] = [
    {id: "ALL", label: "전체"},
    {id: "출입구/게이트", label: "출입구"},
    {id: "지하철/대중교통", label: "대중교통"},
    {id: "먹거리/주차", label: "먹거리/주차"},
];

const LEVEL_PRIORITY: Record<CongestionLevel, number> = {
    붐빔: 4,
    "약간 붐빔": 3,
    보통: 2,
    여유: 1,
};

interface CongestionSpotListProps {
    spots: StadiumZoneSpot[];
    selectedSpotId: string | null;
    error: boolean;
    onSelect: (spotId: string) => void;
    onRetry: () => void;
}

/** 혼잡도 거점을 카테고리와 혼잡도 순서로 탐색할 수 있게 표시한다. */
export function CongestionSpotList({
                                       spots,
                                       selectedSpotId,
                                       error,
                                       onSelect,
                                       onRetry,
                                   }: CongestionSpotListProps) {
    const [sortOption, setSortOption] = useState<SortOption>("default");
    const [selectedCategory, setSelectedCategory] =
        useState<FilterCategory>("ALL");

    const filteredSpots = useMemo(() => {
        const filtered =
            selectedCategory === "ALL"
                ? [...spots]
                : spots.filter((spot) => spot.category === selectedCategory);

        if (sortOption === "busy") {
            return filtered.sort(
                (a, b) =>
                    LEVEL_PRIORITY[b.congestionLevel] -
                    LEVEL_PRIORITY[a.congestionLevel],
            );
        }
        if (sortOption === "free") {
            return filtered.sort(
                (a, b) =>
                    LEVEL_PRIORITY[a.congestionLevel] -
                    LEVEL_PRIORITY[b.congestionLevel],
            );
        }
        return filtered;
    }, [spots, selectedCategory, sortOption]);

    return (
        <div className="flex flex-col border-r border-border bg-surface/50 max-lg:border-r-0 max-lg:border-b">
            <div className="scrollbar-none flex items-center gap-1.5 overflow-x-auto border-b border-border/70 p-3">
                {CATEGORY_TABS.map((tab) => (
                    <button
                        className={`cursor-pointer whitespace-nowrap rounded-full px-3 py-1 text-xs font-bold transition-all ${
                            selectedCategory === tab.id
                                ? "bg-brand text-white shadow-sm"
                                : "bg-surface-elevated text-muted-foreground hover:text-foreground"
                        }`}
                        key={tab.id}
                        onClick={() => setSelectedCategory(tab.id)}
                        type="button"
                    >
                        {tab.label}
                    </button>
                ))}

                <div className="ml-auto flex items-center gap-1 whitespace-nowrap pl-2 text-[11px] text-muted-foreground">
                    <button
                        className={`cursor-pointer font-bold ${
                            sortOption === "default"
                                ? "text-brand"
                                : "text-muted-foreground hover:text-foreground"
                        }`}
                        onClick={() => setSortOption("default")}
                        type="button"
                    >
                        기본순
                    </button>
                    <span>·</span>
                    <button
                        className={`cursor-pointer font-bold ${
                            sortOption === "busy"
                                ? "text-brand"
                                : "text-muted-foreground hover:text-foreground"
                        }`}
                        onClick={() => setSortOption("busy")}
                        type="button"
                    >
                        붐빔순
                    </button>
                </div>
            </div>

            {error && (
                <div className="m-3 flex items-center justify-between rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
                    <span>혼잡도 데이터를 불러오지 못했습니다.</span>
                    <Button
                        className="h-auto p-0 text-destructive"
                        onClick={onRetry}
                        size="sm"
                        type="button"
                        variant="link"
                    >
                        재시도
                    </Button>
                </div>
            )}

            <div className="max-h-[520px] flex-1 space-y-2.5 overflow-y-auto p-3">
                {filteredSpots.map((spot) => {
                    const selected = selectedSpotId === spot.id;

                    return (
                        <div
                            className={`group relative flex cursor-pointer flex-col gap-2 rounded-xl border p-3.5 transition-all ${
                                selected
                                    ? "border-brand bg-brand/5 shadow-sm ring-1 ring-brand/30"
                                    : "border-border/80 bg-surface hover:border-border hover:bg-surface-elevated"
                            }`}
                            key={spot.id}
                            onClick={() => onSelect(spot.id)}
                            onKeyDown={(event) => {
                                if (event.key === "Enter" || event.key === " ") {
                                    event.preventDefault();
                                    onSelect(spot.id);
                                }
                            }}
                            role="button"
                            tabIndex={0}
                        >
                            <div className="flex items-center justify-between gap-2">
                                <div className="flex min-w-0 items-center gap-1.5">
                                    <span className="rounded border border-border/60 bg-surface-elevated px-1.5 py-0.5 text-[10px] font-bold text-muted-foreground">
                                        [{spot.category}]
                                    </span>
                                    <strong className="truncate text-xs font-extrabold text-foreground transition-colors group-hover:text-brand">
                                        {spot.name}
                                    </strong>
                                </div>
                                <CongestionBadge level={spot.congestionLevel}/>
                            </div>

                            <p className="text-xs leading-relaxed text-muted-foreground">
                                {spot.description}
                            </p>
                            <div className="rounded-lg border border-border/60 bg-surface-elevated/80 p-2 text-xs">
                                <div className="flex items-start gap-1">
                                    <span className="shrink-0 text-[11px] font-extrabold text-brand">
                                        동선 팁:
                                    </span>
                                    <span className="text-[11px] font-medium text-foreground/90">
                                        {spot.guideTip}
                                    </span>
                                </div>
                            </div>
                            <div className="flex items-center justify-between pt-0.5 font-mono text-[11px] text-muted-foreground">
                                <span>⏱️ {spot.waitTimeEst}</span>
                                <span className="font-medium text-brand">
                                    {selected ? "선택됨" : "위치 보기 →"}
                                </span>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
