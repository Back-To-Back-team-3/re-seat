"use client";

import {useCallback, useEffect, useRef, useState} from "react";
import {ChevronLeft, ChevronRight} from "lucide-react";

import {Button} from "@/components/ui/button";
import {
    GAME_STATUS_BADGE_CLASSES,
    GAME_STATUS_META,
} from "@/lib/game-status";
import {KST_TIME_ZONE} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";

type TodayGamesPanelProps = {
    games: GameSummary[];
    selectedGameId: number | null;
    onSelect: (game: GameSummary) => void;
};

/**
 * 홈 본문에서 오늘(KST) 경기만 추려 보여주고, 카드를 누르면 히어로의 선택
 * 경기를 바꾼다.
 *
 * 목록 전체를 필터링·정렬하는 GameList와는 책임이 다른, 오늘 하루짜리 요약
 * 패널이라 별도 컴포넌트로 분리했다. "오늘"의 기준은 games-page.tsx가
 * 넘겨주는 games 배열(이미 오늘 것만 필터링됨)을 그대로 신뢰하고, 이 컴포넌트는
 * 날짜 계산을 다시 하지 않는다.
 */
export function TodayGamesPanel({
    games,
    selectedGameId,
    onSelect,
}: TodayGamesPanelProps) {
    const listRef = useRef<HTMLUListElement>(null);
    const [canScrollLeft, setCanScrollLeft] = useState(false);
    const [canScrollRight, setCanScrollRight] = useState(false);
    const heading = new Intl.DateTimeFormat("ko-KR", {
        timeZone: KST_TIME_ZONE,
        year: "numeric",
        month: "long",
        day: "numeric",
    }).format(new Date());

    const updateScrollControls = useCallback(() => {
        const list = listRef.current;

        if (!list) {
            return;
        }

        const endPosition = list.scrollWidth - list.clientWidth;
        setCanScrollLeft(list.scrollLeft > 2);
        setCanScrollRight(endPosition - list.scrollLeft > 2);
    }, []);

    useEffect(() => {
        updateScrollControls();

        const list = listRef.current;
        if (!list) {
            return;
        }

        const resizeObserver =
            typeof ResizeObserver === "undefined"
                ? null
                : new ResizeObserver(updateScrollControls);
        resizeObserver?.observe(list);
        window.addEventListener("resize", updateScrollControls);

        return () => {
            resizeObserver?.disconnect();
            window.removeEventListener("resize", updateScrollControls);
        };
    }, [games, updateScrollControls]);

    function scrollGames(direction: -1 | 1) {
        const list = listRef.current;
        if (!list) {
            return;
        }

        list.scrollBy({
            behavior: "smooth",
            left: direction * Math.max(list.clientWidth * 0.85, 260),
        });
    }

    return (
        <section className="relative z-[2] min-w-0">
            <div className="mb-4 flex items-center justify-between gap-4 max-sm:items-end">
                <span className="text-[13px] font-extrabold text-brand">
                    — 오늘의 경기
                </span>
                <strong className="text-xs font-semibold text-muted-foreground">
                    {heading}
                </strong>
            </div>
            {games.length === 0 ? (
                <p className="m-0 px-5 py-[34px] text-center text-[15px] text-muted-foreground">
                    오늘 예정된 경기가 없습니다.
                </p>
            ) : (
                <div className="grid grid-cols-[44px_minmax(0,1fr)_44px] items-center gap-2 max-sm:grid-cols-[36px_minmax(0,1fr)_36px] max-sm:gap-1">
                    <div className="grid place-items-center">
                        {canScrollLeft && (
                            <Button
                                aria-label="이전 경기 보기"
                                className="border-white/50 bg-surface/80 shadow-card backdrop-blur-md hover:bg-surface max-sm:size-9"
                                onClick={() => scrollGames(-1)}
                                size="icon"
                                type="button"
                                variant="outline"
                            >
                                <ChevronLeft aria-hidden="true"/>
                            </Button>
                        )}
                    </div>
                    <ul
                        aria-label="오늘 경기 목록"
                        className="flex min-w-0 snap-x snap-mandatory gap-4 overflow-x-auto px-1 py-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
                        onScroll={updateScrollControls}
                        ref={listRef}
                    >
                        {games.map((game) => (
                            <li
                                className="w-[calc((100%-32px)/3)] min-w-[260px] shrink-0 snap-start max-sm:w-[85%] max-sm:min-w-[85%]"
                                key={game.gameId}
                            >
                                <button
                                    className={`grid size-full min-w-0 cursor-pointer gap-3 rounded-control border bg-surface px-5 py-4 text-left text-foreground shadow-sm transition-colors hover:border-brand/40 hover:bg-brand/[0.04] ${
                                        selectedGameId === game.gameId
                                            ? "border-brand/40 bg-brand/[0.06]"
                                            : "border-border"
                                    }`}
                                    onClick={() => onSelect(game)}
                                    type="button"
                                >
                                    <span
                                        className={`w-fit justify-self-start rounded-full px-[9px] py-[5px] text-[11px] font-black ${GAME_STATUS_BADGE_CLASSES[game.bookingStatus]}`}
                                    >
                                        {GAME_STATUS_META[game.bookingStatus].label}
                                    </span>
                                    <strong className="truncate text-sm">
                                        {game.homeTeam.name}
                                        <em className="mx-[5px] text-[11px] text-brand not-italic">
                                            VS
                                        </em>
                                        {game.awayTeam.name}
                                    </strong>
                                    <small className="truncate text-[11px] text-muted-foreground">
                                        {formatGameDate(game.gameAt)} ·{" "}
                                        {game.stadium.name}
                                    </small>
                                </button>
                            </li>
                        ))}
                    </ul>

                    <div className="grid place-items-center">
                        {canScrollRight && (
                            <Button
                                aria-label="다음 경기 보기"
                                className="border-white/50 bg-surface/80 shadow-card backdrop-blur-md hover:bg-surface max-sm:size-9"
                                onClick={() => scrollGames(1)}
                                size="icon"
                                type="button"
                                variant="outline"
                            >
                                <ChevronRight aria-hidden="true"/>
                            </Button>
                        )}
                    </div>
                </div>
            )}
        </section>
    );
}
