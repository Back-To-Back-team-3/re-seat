"use client";

import Script from "next/script";
import {useEffect, useRef, useState} from "react";

import {
    CONGESTION_CONFIG,
    CongestionBadge,
} from "@/components/congestion/congestion-badge";
import {useStadiumCongestion} from "@/hooks/use-stadium-congestion";
import {STADIUM_IMAGE_URL} from "@/lib/constants";

interface StadiumCongestionMapProps {
    stadiumNum?: number;
    className?: string;
}

const DEFAULT_LATITUDE = 37.5121;
const DEFAULT_LONGITUDE = 127.0719;

export function StadiumCongestionMap({
    stadiumNum = 1,
    className = "",
}: StadiumCongestionMapProps) {
    const mapContainerRef = useRef<HTMLDivElement>(null);
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const mapInstanceRef = useRef<any>(null);
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const markerInstanceRef = useRef<any>(null);
    /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
    const overlayInstanceRef = useRef<any>(null);
    const [sdkLoaded, setSdkLoaded] = useState(
        () => typeof window !== "undefined" && Boolean(window.kakao?.maps),
    );
    const [sdkError, setSdkError] = useState(false);
    const [mapReady, setMapReady] = useState(false);

    const {
        data: congestion,
        isLoading,
        error,
        refetch,
    } = useStadiumCongestion(stadiumNum);

    const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY;

    // 1. SDK가 로드되면 기본 잠실야구장 좌표로 지도를 먼저 초기화
    useEffect(() => {
        if (!sdkLoaded || !mapContainerRef.current) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        kakao.maps.load(() => {
            const container = mapContainerRef.current;
            if (!container || !kakao.maps) return;

            if (!mapInstanceRef.current) {
                const center = new kakao.maps.LatLng(
                    DEFAULT_LATITUDE,
                    DEFAULT_LONGITUDE,
                );

                const map = new kakao.maps.Map(container, {
                    center,
                    level: 4,
                });
                mapInstanceRef.current = map;

                const marker = new kakao.maps.Marker({
                    position: center,
                    map,
                });
                markerInstanceRef.current = marker;
            }
            setMapReady(true);
        });
    }, [sdkLoaded]);

    // 2. 백엔드 혼잡도 데이터가 도착하면 좌표 이동 및 커스텀 오버레이 렌더링
    useEffect(() => {
        if (!mapReady || !congestion || !mapInstanceRef.current) return;

        const kakao = window.kakao;
        if (!kakao?.maps) return;

        const center = new kakao.maps.LatLng(
            congestion.latitude ?? DEFAULT_LATITUDE,
            congestion.longitude ?? DEFAULT_LONGITUDE,
        );

        mapInstanceRef.current.setCenter(center);
        mapInstanceRef.current.relayout();

        if (markerInstanceRef.current) {
            markerInstanceRef.current.setPosition(center);
        }

        // 기존 오버레이가 있다면 지도에서 제거
        if (overlayInstanceRef.current) {
            overlayInstanceRef.current.setMap(null);
        }

        const populationText =
            congestion.populationMin != null && congestion.populationMax != null
                ? `${congestion.populationMin.toLocaleString()} ~ ${congestion.populationMax.toLocaleString()}명`
                : "정보 없음";

        const badgeConfig =
            CONGESTION_CONFIG[congestion.congestionLevel] ??
            CONGESTION_CONFIG["보통"];

        // 안전한 DOM API를 통한 오버레이 요소 구성 (XSS 방지)
        const overlayContent = document.createElement("div");
        overlayContent.className =
            "relative -translate-y-3 rounded-xl border border-border/80 bg-surface/95 p-3.5 shadow-2xl backdrop-blur-md text-foreground min-w-[240px] max-w-[280px] pointer-events-auto transition-all";

        // 헤더 영역
        const headerEl = document.createElement("div");
        headerEl.className =
            "flex items-center justify-between gap-2 border-b border-border/60 pb-2 mb-2";

        const titleEl = document.createElement("span");
        titleEl.className = "font-extrabold text-sm truncate";
        titleEl.textContent = congestion.stadiumName;

        const badgeEl = document.createElement("span");
        badgeEl.className = `inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-bold border ${badgeConfig.colorClass}`;

        const dotEl = document.createElement("span");
        dotEl.className = `size-1.5 rounded-full ${badgeConfig.dotClass}`;

        const badgeText = document.createTextNode(badgeConfig.label);
        badgeEl.appendChild(dotEl);
        badgeEl.appendChild(badgeText);

        headerEl.appendChild(titleEl);
        headerEl.appendChild(badgeEl);

        // 메시지 본문
        const messageEl = document.createElement("p");
        messageEl.className =
            "text-xs text-muted-foreground leading-relaxed line-clamp-2 mb-2";
        messageEl.textContent =
            congestion.congestionMessage || "실시간 인구 혼잡도 정보입니다.";

        // 하단 정보 (실시간 인구 & 갱신시각)
        const footerEl = document.createElement("div");
        footerEl.className =
            "flex items-center justify-between text-[11px] text-muted-foreground font-mono";

        const popLabel = document.createElement("span");
        popLabel.textContent = "실시간 인구: ";
        const popValue = document.createElement("strong");
        popValue.className = "text-foreground";
        popValue.textContent = populationText;
        popLabel.appendChild(popValue);

        const timeEl = document.createElement("span");
        timeEl.textContent = congestion.observedAt
            ? congestion.observedAt.slice(11, 16)
            : "";

        footerEl.appendChild(popLabel);
        footerEl.appendChild(timeEl);

        overlayContent.appendChild(headerEl);
        overlayContent.appendChild(messageEl);
        overlayContent.appendChild(footerEl);

        const overlay = new kakao.maps.CustomOverlay({
            position: center,
            content: overlayContent,
            map: mapInstanceRef.current,
            yAnchor: 1.3,
        });
        overlayInstanceRef.current = overlay;

        const marker = markerInstanceRef.current;
        const currentMap = mapInstanceRef.current;
        const clickHandler = () => {
            overlay.setMap(currentMap);
        };

        if (marker && kakao.maps.event?.addListener) {
            kakao.maps.event.addListener(marker, "click", clickHandler);
        }

        return () => {
            overlay.setMap(null);
            if (marker && kakao.maps.event?.removeListener) {
                kakao.maps.event.removeListener(marker, "click", clickHandler);
            }
        };
    }, [mapReady, congestion]);

    // 카카오 API 키가 없거나 SDK 로드 실패 시 정적 이미지 폴백
    if (!kakaoApiKey || sdkError) {
        return (
            <figure
                className={`relative m-0 h-[210px] overflow-hidden rounded-[18px] shadow-card after:absolute after:inset-0 after:bg-[linear-gradient(180deg,transparent_40%,rgba(9,13,21,0.7))] after:content-[''] max-sm:h-[170px] ${className}`}
            >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                    alt="잠실야구장 경기 전경"
                    className="size-full object-cover"
                    src={STADIUM_IMAGE_URL}
                />

                <figcaption className="absolute inset-x-[18px] bottom-[14px] z-[1] flex justify-between text-xs text-white">
                    <span className="font-extrabold tracking-[0.12em]">
                        JAMSIL
                    </span>
                    <span>실시간 지도 준비 중</span>
                </figcaption>
            </figure>
        );
    }

    return (
        <div
            className={`relative h-[210px] w-full overflow-hidden rounded-[18px] border border-border bg-surface shadow-card max-sm:h-[170px] ${className}`}
        >
            <Script
                id="kakao-maps-sdk-small"
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

            <div className="size-full h-full w-full" ref={mapContainerRef} />

            {/* 로딩 표시 (지도 위 작은 뱃지) */}
            {isLoading && (
                <div className="absolute top-3 left-3 z-10 flex items-center gap-2 rounded-lg border border-border/70 bg-surface/90 px-2.5 py-1 text-xs text-muted-foreground backdrop-blur-md">
                    <div className="size-3 animate-spin rounded-full border-2 border-brand border-t-transparent" />
                    혼잡도 정보 로딩 중...
                </div>
            )}

            {/* API 에러 시 (지도는 유지하고 재시도 버튼만 표시) */}
            {error && !isLoading && (
                <div className="absolute top-3 left-3 z-10 flex items-center gap-2 rounded-lg border border-border/70 bg-surface/90 px-2.5 py-1 text-xs text-muted-foreground backdrop-blur-md">
                    <span>혼잡도 조회 실패</span>
                    <button
                        className="rounded border border-border px-1.5 py-0.5 text-[11px] font-bold text-brand hover:bg-surface-elevated"
                        onClick={() => void refetch()}
                        type="button"
                    >
                        재시도
                    </button>
                </div>
            )}

            {/* 정상 혼잡도 뱃지 */}
            {congestion && !isLoading && !error && (
                <div className="absolute top-3 left-3 z-10 flex items-center gap-2 rounded-lg border border-border/70 bg-surface/90 px-2.5 py-1 shadow-sm backdrop-blur-md">
                    <span className="text-xs font-bold tracking-tight text-foreground">
                        실시간 혼잡도
                    </span>
                    <CongestionBadge level={congestion.congestionLevel} />
                </div>
            )}
        </div>
    );
}

