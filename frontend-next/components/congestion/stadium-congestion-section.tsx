"use client";

import Script from "next/script";
import {useEffect, useMemo, useRef, useState} from "react";
import {MapPinned} from "lucide-react";

import {CONGESTION_CONFIG} from "@/components/congestion/congestion-badge";
import {CongestionSectionHeader} from "@/components/congestion/congestion-section-header";
import {CongestionSpotList} from "@/components/congestion/congestion-spot-list";
import {Button} from "@/components/ui/button";
import {useStadiumCongestion} from "@/hooks/use-stadium-congestion";
import {calculateStadiumZones} from "@/lib/stadium-zones";

interface StadiumCongestionSectionProps {
    stadiumNum?: number;
    className?: string;
}

const DEFAULT_CENTER_LAT = 37.5122;
const DEFAULT_CENTER_LNG = 127.0725;

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

            // 커스텀 핀 오버레이 (점 + 라벨 태그) - DOM API로 안전하게 생성
            const pinContainer = document.createElement("div");
            pinContainer.className =
                "group relative flex flex-col items-center cursor-pointer transition-transform hover:scale-110 -translate-x-1/2 -translate-y-full";

            const pinPill = document.createElement("div");
            pinPill.className =
                "flex items-center gap-1.5 rounded-full border border-border/80 bg-surface/95 px-2.5 py-1 shadow-md backdrop-blur-md text-[11px] font-bold text-foreground hover:border-brand/60 transition-colors";

            const dot = document.createElement("span");
            dot.className = `size-2 rounded-full ${config.dotClass} animate-pulse`;

            const nameSpan = document.createElement("span");
            nameSpan.textContent = spot.name;

            const levelSpan = document.createElement("span");
            levelSpan.className = `rounded px-1 text-[10px] ${config.colorClass}`;
            levelSpan.textContent = spot.congestionLevel;

            pinPill.appendChild(dot);
            pinPill.appendChild(nameSpan);
            pinPill.appendChild(levelSpan);

            const arrow = document.createElement("div");
            arrow.className =
                "size-2.5 rotate-45 border-r border-b border-border/80 bg-surface/95 -mt-1 shadow-sm";

            pinContainer.appendChild(pinPill);
            pinContainer.appendChild(arrow);

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

        // 상세 팝업 DOM API 안전 구성 (XSS 방지)
        const popupEl = document.createElement("div");
        popupEl.className =
            "relative -translate-y-8 rounded-xl border border-border bg-surface/95 p-3.5 shadow-2xl backdrop-blur-md text-foreground min-w-[260px] max-w-[300px] pointer-events-auto transition-all";

        // 1. 헤더
        const header = document.createElement("div");
        header.className =
            "flex items-center justify-between gap-2 border-b border-border/60 pb-2 mb-2";

        const titleGroup = document.createElement("div");
        titleGroup.className = "flex items-center gap-1.5 truncate";

        const catSpan = document.createElement("span");
        catSpan.className = "text-[11px] text-muted-foreground font-medium";
        catSpan.textContent = `[${selectedSpot.category}]`;

        const nameStrong = document.createElement("strong");
        nameStrong.className = "text-xs truncate font-extrabold";
        nameStrong.textContent = selectedSpot.name;

        titleGroup.appendChild(catSpan);
        titleGroup.appendChild(nameStrong);

        const badge = document.createElement("span");
        badge.className = `inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-bold border ${config.colorClass}`;

        const badgeDot = document.createElement("span");
        badgeDot.className = `size-1.5 rounded-full ${config.dotClass}`;

        badge.appendChild(badgeDot);
        badge.appendChild(document.createTextNode(config.label));

        header.appendChild(titleGroup);
        header.appendChild(badge);

        // 2. 설명
        const descP = document.createElement("p");
        descP.className = "text-xs text-muted-foreground leading-relaxed mb-2";
        descP.textContent = selectedSpot.description;

        // 3. 동선 팁 박스
        const tipBox = document.createElement("div");
        tipBox.className =
            "rounded-lg bg-brand/5 border border-brand/20 p-2 text-xs text-foreground mb-2 leading-tight";

        const tipLabel = document.createElement("span");
        tipLabel.className = "font-extrabold text-brand text-[11px]";
        tipLabel.textContent = "💡 동선 팁:";

        const tipContent = document.createElement("span");
        tipContent.className = "text-[11px] text-foreground block mt-0.5";
        tipContent.textContent = selectedSpot.guideTip;

        tipBox.appendChild(tipLabel);
        tipBox.appendChild(tipContent);

        // 4. 예상 대기시간
        const footer = document.createElement("div");
        footer.className =
            "flex items-center justify-between text-[11px] text-muted-foreground font-mono border-t border-border/40 pt-2";

        const waitLabel = document.createElement("span");
        waitLabel.textContent = "예상 대기/소요";

        const waitStrong = document.createElement("strong");
        waitStrong.className = "text-foreground";
        waitStrong.textContent = selectedSpot.waitTimeEst;

        footer.appendChild(waitLabel);
        footer.appendChild(waitStrong);

        popupEl.appendChild(header);
        popupEl.appendChild(descP);
        popupEl.appendChild(tipBox);
        popupEl.appendChild(footer);

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

            {/* 2. 메인 컨텐츠: 좌측 리스트 (420px) + 우측 지도 뷰 (1fr) */}
            <div className="grid grid-cols-[420px_1fr] max-lg:grid-cols-1 min-h-[580px]">
                <CongestionSpotList
                    error={Boolean(error)}
                    onRetry={() => void refetch()}
                    onSelect={setSelectedSpotId}
                    selectedSpotId={selectedSpotId}
                    spots={zoneSpots}
                />

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
                    <Button
                        className="absolute right-4 bottom-4 z-10 bg-surface/90 shadow-md backdrop-blur-md"
                        onClick={resetMapCenter}
                        size="sm"
                        title="전체 거점 중심으로 이동"
                        type="button"
                        variant="outline"
                    >
                        <MapPinned aria-hidden="true"/>
                        전체 거점 보기
                    </Button>

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
