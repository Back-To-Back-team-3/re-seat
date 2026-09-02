"use client";

import Script from "next/script";
import {useEffect, useMemo, useRef, useState} from "react";

import {
    CONGESTION_CONFIG,
    CongestionBadge,
} from "@/components/congestion/congestion-badge";
import {useStadiumCongestion} from "@/hooks/use-stadium-congestion";
import {calculateStadiumZones} from "@/lib/stadium-zones";
import type {
    CongestionLevel,
    ZoneCategory,
} from "@/types/congestion";

interface StadiumCongestionSectionProps {
    stadiumNum?: number;
    className?: string;
}

const DEFAULT_CENTER_LAT = 37.5122;
const DEFAULT_CENTER_LNG = 127.0725;

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

export function StadiumCongestionSection({
    stadiumNum = 1,
    className = "",
}: StadiumCongestionSectionProps) {
    const mapContainerRef = useRef<HTMLDivElement>(null);
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const mapInstanceRef = useRef<any>(null);
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const overlaysRef = useRef<Map<string, any>>(new Map());
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const detailOverlayRef = useRef<any>(null);

    const [sdkLoaded, setSdkLoaded] = useState(
        () => typeof window !== "undefined" && Boolean(window.kakao?.maps),
    );
    const [sdkError, setSdkError] = useState(false);
    const [mapReady, setMapReady] = useState(false);
    const [selectedSpotId, setSelectedSpotId] = useState<string | null>(null);
    const [sortOption, setSortOption] = useState<SortOption>("default");
    const [selectedCategory, setSelectedCategory] =
        useState<FilterCategory>("ALL");

    const {
        data: congestion,
        isLoading,
        error,
        refetch,
    } = useStadiumCongestion(stadiumNum);

    const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY;

    // 구역별 혼잡도 및 동선 가이드 계산
    const zoneSpots = useMemo(() => {
        return calculateStadiumZones(congestion);
    }, [congestion]);

    // 필터 및 정렬된 구역 목록
    const filteredSpots = useMemo(() => {
        let list = [...zoneSpots];

        if (selectedCategory !== "ALL") {
            list = list.filter((spot) => spot.category === selectedCategory);
        }

        if (sortOption === "busy") {
            list.sort(
                (a, b) =>
                    LEVEL_PRIORITY[b.congestionLevel] -
                    LEVEL_PRIORITY[a.congestionLevel],
            );
        } else if (sortOption === "free") {
            list.sort(
                (a, b) =>
                    LEVEL_PRIORITY[a.congestionLevel] -
                    LEVEL_PRIORITY[b.congestionLevel],
            );
        }

        return list;
    }, [zoneSpots, selectedCategory, sortOption]);

    // 1. 지도 초기화
    useEffect(() => {
        if (!sdkLoaded || !mapContainerRef.current) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        kakao.maps.load(() => {
            const container = mapContainerRef.current;
            if (!container || !kakao.maps) return;

            if (!mapInstanceRef.current) {
                const center = new kakao.maps.LatLng(
                    DEFAULT_CENTER_LAT,
                    DEFAULT_CENTER_LNG,
                );

                const map = new kakao.maps.Map(container, {
                    center,
                    level: 4,
                });
                mapInstanceRef.current = map;
            }
            setMapReady(true);
        });
    }, [sdkLoaded]);

    // 2. 구역별 마커 및 라벨 커스텀 오버레이 렌더링
    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || zoneSpots.length === 0)
            return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        const map = mapInstanceRef.current;

        // 기존 오버레이 제거
        overlaysRef.current.forEach((overlay) => overlay.setMap(null));
        overlaysRef.current.clear();

        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
            detailOverlayRef.current = null;
        }

        zoneSpots.forEach((spot) => {
            const position = new kakao.maps.LatLng(
                spot.latitude,
                spot.longitude,
            );
            const config =
                CONGESTION_CONFIG[spot.congestionLevel] ??
                CONGESTION_CONFIG["보통"];

            // 커스텀 핀 오버레이 (점 + 라벨 태그)
            const pinContainer = document.createElement("div");
            pinContainer.className =
                "group relative flex flex-col items-center cursor-pointer transition-transform hover:scale-110 -translate-x-1/2 -translate-y-full";

            pinContainer.innerHTML = `
                <div class="flex items-center gap-1.5 rounded-full border border-border/80 bg-surface/95 px-2.5 py-1 shadow-md backdrop-blur-md text-[11px] font-bold text-foreground hover:border-brand/60 transition-colors">
                    <span class="size-2 rounded-full ${config.dotClass} animate-pulse"></span>
                    <span>${spot.name}</span>
                    <span class="rounded px-1 text-[10px] ${config.colorClass}">${spot.congestionLevel}</span>
                </div>
                <div class="size-2.5 rotate-45 border-r border-b border-border/80 bg-surface/95 -mt-1 shadow-sm"></div>
            `;

            pinContainer.onclick = () => {
                setSelectedSpotId(spot.id);
            };

            const overlay = new kakao.maps.CustomOverlay({
                position,
                content: pinContainer,
                map,
                yAnchor: 1,
            });

            overlaysRef.current.set(spot.id, overlay);
        });
    }, [mapReady, zoneSpots]);

    // 3. 선택된 구역이 변경되면 지도 이동 및 상세 팝업 표시
    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || !selectedSpotId) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        const map = mapInstanceRef.current;
        const selectedSpot = zoneSpots.find((s) => s.id === selectedSpotId);
        if (!selectedSpot) return;

        const position = new kakao.maps.LatLng(
            selectedSpot.latitude,
            selectedSpot.longitude,
        );

        map.panTo(position);

        // 기존 상세 팝업 제거
        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
        }

        const config =
            CONGESTION_CONFIG[selectedSpot.congestionLevel] ??
            CONGESTION_CONFIG["보통"];

        const popupEl = document.createElement("div");
        popupEl.className =
            "relative -translate-y-8 rounded-xl border border-border bg-surface/95 p-3.5 shadow-2xl backdrop-blur-md text-foreground min-w-[260px] max-w-[300px] pointer-events-auto transition-all";
        popupEl.innerHTML = `
            <div class="flex items-center justify-between gap-2 border-b border-border/60 pb-2 mb-2">
                <div class="flex items-center gap-1.5 truncate">
                    <span class="text-[11px] text-muted-foreground font-medium">[${selectedSpot.category}]</span>
                    <strong class="text-xs truncate font-extrabold">${selectedSpot.name}</strong>
                </div>
                <span class="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-bold border ${config.colorClass}">
                    <span class="size-1.5 rounded-full ${config.dotClass}"></span>
                    ${config.label}
                </span>
            </div>
            <p class="text-xs text-muted-foreground leading-relaxed mb-2">
                ${selectedSpot.description}
            </p>
            <div class="rounded-lg bg-brand/5 border border-brand/20 p-2 text-xs text-foreground mb-2 leading-tight">
                <span class="font-extrabold text-brand text-[11px]">💡 동선 팁:</span>
                <span class="text-[11px] text-foreground block mt-0.5">${selectedSpot.guideTip}</span>
            </div>
            <div class="flex items-center justify-between text-[11px] text-muted-foreground font-mono border-t border-border/40 pt-2">
                <span>예상 대기/소요</span>
                <strong class="text-foreground">${selectedSpot.waitTimeEst}</strong>
            </div>
        `;

        const detailOverlay = new kakao.maps.CustomOverlay({
            position,
            content: popupEl,
            map,
            yAnchor: 1.2,
        });

        detailOverlayRef.current = detailOverlay;
    }, [mapReady, selectedSpotId, zoneSpots]);

    const resetMapCenter = () => {
        if (!mapInstanceRef.current || !window.kakao?.maps) return;
        const center = new window.kakao.maps.LatLng(
            DEFAULT_CENTER_LAT,
            DEFAULT_CENTER_LNG,
        );
        mapInstanceRef.current.panTo(center);
        mapInstanceRef.current.setLevel(4);
        setSelectedSpotId(null);
        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
            detailOverlayRef.current = null;
        }
    };

    const overallPopulationText =
        congestion?.populationMin != null && congestion?.populationMax != null
            ? `${congestion.populationMin.toLocaleString()} ~ ${congestion.populationMax.toLocaleString()}명`
            : null;

    return (
        <section
            aria-label="잠실야구장 주변 실시간 구역별 혼잡도"
            className={`overflow-hidden rounded-panel border border-border bg-surface shadow-card ${className}`}
        >
            {/* 1. 상단 대시보드 헤더 */}
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border px-6 py-5 max-sm:px-4 max-sm:py-4">
                <div className="grid gap-1">
                    <div className="flex flex-wrap items-center gap-2.5">
                        <span className="inline-block size-2 rounded-full bg-brand animate-pulse" />
                        <h2 className="text-lg font-black tracking-tight text-foreground max-sm:text-base">
                            잠실야구장 주변 실시간 구역별 혼잡도
                        </h2>
                        {congestion && (
                            <CongestionBadge
                                level={congestion.congestionLevel}
                            />
                        )}
                    </div>
                    <p className="text-xs text-muted-foreground">
                        서울시 실시간 도시데이터 기반 잠실종합운동장 전체 인구 현황과
                        주요 게이트·지하철역 이동 팁을 안내합니다.
                        {overallPopulationText && (
                            <span className="ml-1 text-foreground/90 font-mono font-medium">
                                (실시간 인구: 약 {overallPopulationText})
                            </span>
                        )}
                    </p>
                </div>

                {/* 상태 및 컨트롤 */}
                <div className="flex items-center gap-3 max-sm:w-full max-sm:justify-between">
                    {/* 혼잡도 범례 */}
                    <div className="flex items-center gap-2 rounded-lg border border-border bg-surface-elevated px-3 py-1.5 text-[11px] font-medium text-muted-foreground max-sm:hidden">
                        <span className="flex items-center gap-1">
                            <span className="size-1.5 rounded-full bg-emerald-500" />
                            여유
                        </span>
                        <span className="flex items-center gap-1">
                            <span className="size-1.5 rounded-full bg-blue-500" />
                            보통
                        </span>
                        <span className="flex items-center gap-1">
                            <span className="size-1.5 rounded-full bg-amber-500" />
                            약간 붐빔
                        </span>
                        <span className="flex items-center gap-1">
                            <span className="size-1.5 rounded-full bg-brand" />
                            붐빔
                        </span>
                    </div>

                    {congestion?.observedAt && (
                        <span className="text-xs text-muted-foreground font-mono">
                            {congestion.observedAt.slice(11, 16)} 갱신
                        </span>
                    )}

                    <button
                        className="inline-flex cursor-pointer items-center gap-1.5 rounded-control border border-border bg-surface-elevated px-3 py-1.5 text-xs font-bold text-foreground hover:border-brand/40 transition-colors"
                        onClick={() => void refetch()}
                        type="button"
                    >
                        {isLoading ? (
                            <span className="size-3 animate-spin rounded-full border-2 border-brand border-t-transparent" />
                        ) : (
                            <span>새로고침</span>
                        )}
                    </button>
                </div>
            </div>

            {/* 2. 메인 컨텐츠: 좌측 리스트 (420px) + 우측 지도 뷰 (1fr) */}
            <div className="grid grid-cols-[420px_1fr] max-lg:grid-cols-1 min-h-[580px]">
                {/* 좌측: 구역 필터 및 목록 패널 */}
                <div className="flex flex-col border-r border-border max-lg:border-r-0 max-lg:border-b bg-surface/50">
                    {/* 카테고리 필터 탭 */}
                    <div className="flex items-center gap-1.5 border-b border-border/70 p-3 overflow-x-auto scrollbar-none">
                        {CATEGORY_TABS.map((tab) => (
                            <button
                                className={`cursor-pointer rounded-full px-3 py-1 text-xs font-bold transition-all whitespace-nowrap ${
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

                        <div className="ml-auto flex items-center gap-1 text-[11px] text-muted-foreground pl-2 whitespace-nowrap">
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

                    {/* 에러 상태 안내 */}
                    {error && (
                        <div className="m-3 flex items-center justify-between rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
                            <span>혼잡도 데이터를 불러오지 못했습니다.</span>
                            <button
                                className="font-bold underline cursor-pointer"
                                onClick={() => void refetch()}
                                type="button"
                            >
                                재시도
                            </button>
                        </div>
                    )}

                    {/* 구역 리스트 */}
                    <div className="flex-1 overflow-y-auto max-h-[520px] p-3 space-y-2.5">
                        {filteredSpots.map((spot) => {
                            const isSelected = selectedSpotId === spot.id;

                            return (
                                <div
                                    className={`group relative flex cursor-pointer flex-col gap-2 rounded-xl border p-3.5 transition-all ${
                                        isSelected
                                            ? "border-brand bg-brand/5 shadow-sm ring-1 ring-brand/30"
                                            : "border-border/80 bg-surface hover:border-border hover:bg-surface-elevated"
                                    }`}
                                    key={spot.id}
                                    onClick={() => setSelectedSpotId(spot.id)}
                                    role="button"
                                    tabIndex={0}
                                >
                                    <div className="flex items-center justify-between gap-2">
                                        <div className="flex items-center gap-1.5 min-w-0">
                                            <span className="text-[10px] font-bold text-muted-foreground bg-surface-elevated px-1.5 py-0.5 rounded border border-border/60">
                                                [{spot.category}]
                                            </span>
                                            <strong className="text-xs truncate font-extrabold text-foreground group-hover:text-brand transition-colors">
                                                {spot.name}
                                            </strong>
                                        </div>
                                        <CongestionBadge
                                            level={spot.congestionLevel}
                                        />
                                    </div>

                                    <p className="text-xs text-muted-foreground leading-relaxed">
                                        {spot.description}
                                    </p>

                                    {/* 💡 동선 가이드 팁 */}
                                    <div className="rounded-lg bg-surface-elevated/80 border border-border/60 p-2 text-xs">
                                        <div className="flex items-start gap-1">
                                            <span className="font-extrabold text-brand text-[11px] shrink-0">
                                                동선 팁:
                                            </span>
                                            <span className="text-[11px] text-foreground/90 font-medium">
                                                {spot.guideTip}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="flex items-center justify-between text-[11px] text-muted-foreground font-mono pt-0.5">
                                        <span>⏱️ {spot.waitTimeEst}</span>
                                        <span className="text-brand font-medium">
                                            {isSelected ? "선택됨" : "위치 보기 →"}
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* 우측: 카카오 지도 뷰 */}
                <div className="relative min-h-[460px] w-full bg-surface-elevated max-lg:min-h-[380px]">
                    <Script
                        id="kakao-maps-sdk"
                        onError={() => {
                            console.error("카카오 지도 SDK 로드 실패");
                            setSdkError(true);
                        }}
                        onLoad={() => {
                            setSdkLoaded(true);
                        }}
                        onReady={() => {
                            setSdkLoaded(true);
                        }}
                        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoApiKey}&autoload=false`}
                        strategy="afterInteractive"
                    />

                    {/* 지도 컨테이너 */}
                    <div
                        className="size-full h-full w-full"
                        ref={mapContainerRef}
                    />

                    {/* 지도 컨트롤: 전체 위치 리셋 버튼 */}
                    <button
                        className="absolute bottom-4 right-4 z-10 flex cursor-pointer items-center gap-1.5 rounded-lg border border-border/80 bg-surface/90 px-3 py-1.5 text-xs font-bold text-foreground shadow-md backdrop-blur-md hover:bg-surface-elevated transition-all"
                        onClick={resetMapCenter}
                        title="전체 거점 중심으로 이동"
                        type="button"
                    >
                        <span>📍 전체 거점 보기</span>
                    </button>

                    {/* SDK 로드 실패 시 안내 */}
                    {(!kakaoApiKey || sdkError) && (
                        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-surface/95 p-6 text-center text-muted-foreground">
                            <span className="text-base font-bold text-foreground">
                                실시간 지도 인터랙션을 준비 중입니다
                            </span>
                            <span className="text-xs">
                                좌측 목록에서 구역별 실시간 혼잡도 및 동선 팁을 확인하실
                                수 있습니다.
                            </span>
                        </div>
                    )}
                </div>
            </div>
        </section>
    );
}
