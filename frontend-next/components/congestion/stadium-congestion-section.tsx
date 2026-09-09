"use client";

import {useMemo, useState} from "react";

import {CongestionSectionHeader} from "@/components/congestion/congestion-section-header";
import {CongestionSpotList} from "@/components/congestion/congestion-spot-list";
import {StadiumZoneMap} from "@/components/congestion/stadium-zone-map";
import {useStadiumCongestion} from "@/hooks/use-stadium-congestion";
import {calculateStadiumZones} from "@/lib/stadium-zones";

interface StadiumCongestionSectionProps {
    stadiumNum?: number;
    className?: string;
}

/** 구장 혼잡도 조회 결과를 구역 목록과 지도에 함께 표시한다. */
export function StadiumCongestionSection({
    stadiumNum = 1,
    className = "",
}: StadiumCongestionSectionProps) {
    const [selectedSpotId, setSelectedSpotId] = useState<string | null>(null);
    const {
        data: congestion,
        isLoading,
        error,
        refetch,
    } = useStadiumCongestion(stadiumNum);
    const zoneSpots = useMemo(
        () => calculateStadiumZones(congestion),
        [congestion],
    );

    return (
        <section
            aria-label="잠실야구장 주변 실시간 구역별 혼잡도"
            className={`overflow-hidden rounded-panel border border-border bg-surface shadow-card ${className}`}
        >
            <CongestionSectionHeader
                congestion={congestion}
                loading={isLoading}
                onRefresh={() => void refetch()}
            />

            <div className="grid min-h-145 grid-cols-[420px_1fr] max-lg:grid-cols-1">
                <CongestionSpotList
                    error={Boolean(error)}
                    onRetry={() => void refetch()}
                    onSelect={setSelectedSpotId}
                    selectedSpotId={selectedSpotId}
                    spots={zoneSpots}
                />
                <StadiumZoneMap
                    onSelect={setSelectedSpotId}
                    selectedSpotId={selectedSpotId}
                    spots={zoneSpots}
                />
            </div>
        </section>
    );
}
