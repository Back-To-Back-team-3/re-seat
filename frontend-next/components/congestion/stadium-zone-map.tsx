"use client";

import {MapPinned} from "lucide-react";
import Script from "next/script";
import {useEffect, useRef, useState} from "react";

import {CONGESTION_CONFIG} from "@/components/congestion/congestion-badge";
import {Button} from "@/components/ui/button";
import type {StadiumZoneSpot} from "@/types/congestion";

const DEFAULT_CENTER_LAT = 37.5122;
const DEFAULT_CENTER_LNG = 127.0725;

type StadiumZoneMapProps = {
    spots: StadiumZoneSpot[];
    selectedSpotId: string | null;
    onSelect: (spotId: string | null) => void;
};

/** 지도에 표시할 구역 핀 DOM을 안전한 textContent 기반으로 생성한다. */
function createZonePin(
    spot: StadiumZoneSpot,
    onSelect: (spotId: string) => void,
) {
    const config =
        CONGESTION_CONFIG[spot.congestionLevel] ?? CONGESTION_CONFIG["보통"];
    const container = document.createElement("div");
    container.className =
        "group relative flex -translate-x-1/2 -translate-y-full cursor-pointer flex-col items-center transition-transform hover:scale-110";

    const pill = document.createElement("div");
    pill.className =
        "flex items-center gap-1.5 rounded-full border border-border/80 bg-surface/95 px-2.5 py-1 text-[11px] font-bold text-foreground shadow-md backdrop-blur-md transition-colors hover:border-brand/60";

    const dot = document.createElement("span");
    dot.className = `size-2 animate-pulse rounded-full ${config.dotClass}`;

    const name = document.createElement("span");
    name.textContent = spot.name;

    const level = document.createElement("span");
    level.className = `rounded px-1 text-[10px] ${config.colorClass}`;
    level.textContent = spot.congestionLevel;

    const arrow = document.createElement("div");
    arrow.className =
        "-mt-1 size-2.5 rotate-45 border-r border-b border-border/80 bg-surface/95 shadow-sm";

    pill.appendChild(dot);
    pill.appendChild(name);
    pill.appendChild(level);
    container.appendChild(pill);
    container.appendChild(arrow);
    container.onclick = () => onSelect(spot.id);

    return container;
}

/** 선택 구역의 혼잡도와 이동 정보를 보여주는 상세 오버레이 DOM을 생성한다. */
function createZoneDetail(spot: StadiumZoneSpot) {
    const config =
        CONGESTION_CONFIG[spot.congestionLevel] ?? CONGESTION_CONFIG["보통"];
    const popup = document.createElement("div");
    popup.className =
        "pointer-events-auto relative min-w-65 max-w-75 -translate-y-8 rounded-xl border border-border bg-surface/95 p-3.5 text-foreground shadow-2xl backdrop-blur-md transition-all";

    const header = document.createElement("div");
    header.className =
        "mb-2 flex items-center justify-between gap-2 border-b border-border/60 pb-2";

    const titleGroup = document.createElement("div");
    titleGroup.className = "flex items-center gap-1.5 truncate";

    const category = document.createElement("span");
    category.className = "text-[11px] font-medium text-muted-foreground";
    category.textContent = `[${spot.category}]`;

    const name = document.createElement("strong");
    name.className = "truncate text-xs font-extrabold";
    name.textContent = spot.name;

    const badge = document.createElement("span");
    badge.className = `inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-bold ${config.colorClass}`;

    const badgeDot = document.createElement("span");
    badgeDot.className = `size-1.5 rounded-full ${config.dotClass}`;

    badge.appendChild(badgeDot);
    badge.appendChild(document.createTextNode(config.label));
    titleGroup.appendChild(category);
    titleGroup.appendChild(name);
    header.appendChild(titleGroup);
    header.appendChild(badge);

    const description = document.createElement("p");
    description.className = "mb-2 text-xs leading-relaxed text-muted-foreground";
    description.textContent = spot.description;

    const tip = document.createElement("div");
    tip.className =
        "mb-2 rounded-lg border border-brand/20 bg-brand/5 p-2 text-xs leading-tight text-foreground";

    const tipLabel = document.createElement("span");
    tipLabel.className = "text-[11px] font-extrabold text-brand";
    tipLabel.textContent = "동선 팁:";

    const tipContent = document.createElement("span");
    tipContent.className = "mt-0.5 block text-[11px] text-foreground";
    tipContent.textContent = spot.guideTip;

    tip.appendChild(tipLabel);
    tip.appendChild(tipContent);

    const footer = document.createElement("div");
    footer.className =
        "flex items-center justify-between border-t border-border/40 pt-2 font-mono text-[11px] text-muted-foreground";

    const waitLabel = document.createElement("span");
    waitLabel.textContent = "예상 대기/소요";

    const waitTime = document.createElement("strong");
    waitTime.className = "text-foreground";
    waitTime.textContent = spot.waitTimeEst;

    footer.appendChild(waitLabel);
    footer.appendChild(waitTime);
    popup.appendChild(header);
    popup.appendChild(description);
    popup.appendChild(tip);
    popup.appendChild(footer);

    return popup;
}

/** 카카오 지도와 구역별 핀 및 상세 오버레이의 생명주기를 관리한다. */
export function StadiumZoneMap({
    spots,
    selectedSpotId,
    onSelect,
}: StadiumZoneMapProps) {
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
    const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY;

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
                mapInstanceRef.current = new kakao.maps.Map(container, {
                    center,
                    level: 4,
                });
            }
            setMapReady(true);
        });
    }, [sdkLoaded]);

    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || spots.length === 0) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        const map = mapInstanceRef.current;
        overlaysRef.current.forEach((overlay) => overlay.setMap(null));
        overlaysRef.current.clear();

        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
            detailOverlayRef.current = null;
        }

        spots.forEach((spot) => {
            const position = new kakao.maps.LatLng(spot.latitude, spot.longitude);
            const overlay = new kakao.maps.CustomOverlay({
                position,
                content: createZonePin(spot, onSelect),
                map,
                yAnchor: 1,
            });
            overlaysRef.current.set(spot.id, overlay);
        });
    }, [mapReady, onSelect, spots]);

    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || !selectedSpotId) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        const selectedSpot = spots.find((spot) => spot.id === selectedSpotId);
        if (!selectedSpot) return;

        const position = new kakao.maps.LatLng(
            selectedSpot.latitude,
            selectedSpot.longitude,
        );
        mapInstanceRef.current.panTo(position);

        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
        }

        detailOverlayRef.current = new kakao.maps.CustomOverlay({
            position,
            content: createZoneDetail(selectedSpot),
            map: mapInstanceRef.current,
            yAnchor: 1.2,
        });
    }, [mapReady, selectedSpotId, spots]);

    function resetMapCenter() {
        const kakao = window.kakao;
        if (!mapInstanceRef.current || !kakao?.maps) return;

        const center = new kakao.maps.LatLng(
            DEFAULT_CENTER_LAT,
            DEFAULT_CENTER_LNG,
        );
        mapInstanceRef.current.panTo(center);
        mapInstanceRef.current.setLevel(4);
        onSelect(null);

        if (detailOverlayRef.current) {
            detailOverlayRef.current.setMap(null);
            detailOverlayRef.current = null;
        }
    }

    return (
        <div className="relative min-h-115 w-full bg-surface-elevated max-lg:min-h-95">
            <Script
                id="kakao-maps-sdk"
                onError={() => {
                    console.error("카카오 지도 SDK 로드 실패");
                    setSdkError(true);
                }}
                onLoad={() => setSdkLoaded(true)}
                onReady={() => setSdkLoaded(true)}
                src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoApiKey}&autoload=false`}
                strategy="afterInteractive"
            />

            <div className="size-full h-full w-full" ref={mapContainerRef}/>

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

            {(!kakaoApiKey || sdkError) && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-surface/95 p-6 text-center text-muted-foreground">
                    <span className="text-base font-bold text-foreground">
                        실시간 지도 인터랙션을 준비 중입니다
                    </span>
                    <span className="text-xs">
                        좌측 목록에서 구역별 실시간 혼잡도 및 동선 팁을 확인할 수
                        있습니다.
                    </span>
                </div>
            )}
        </div>
    );
}
