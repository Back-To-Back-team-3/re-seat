"use client";

import {useEffect, useState} from "react";
import {ArrowRight, Pause, Play} from "lucide-react";

import {Button} from "@/components/ui/button";
import {STADIUM_IMAGE_URL} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import {GAME_STATUS_META} from "@/lib/game-status";
import type {GameSummary} from "@/types/game";

type GamesHeroProps = {
    selectedGame: GameSummary | null;
    games: GameSummary[];
    authenticated: boolean;
    bookingBusy: boolean;
    selectedCompleted: boolean;
    onSelect: (game: GameSummary) => void;
    onStartBooking: (game: GameSummary) => void;
};

/** 홈 화면에서 선택한 경기의 예매 진입 영역을 표시한다. */
export function GamesHero({
    selectedGame,
    games,
    authenticated,
    bookingBusy,
    selectedCompleted,
    onSelect,
    onStartBooking,
}: GamesHeroProps) {
    const [interactionPaused, setInteractionPaused] = useState(false);
    const [manuallyPaused, setManuallyPaused] = useState(false);
    const selectedMeta = selectedGame
        ? GAME_STATUS_META[selectedGame.bookingStatus]
        : null;
    const canRotate = games.length > 1;

    useEffect(() => {
        const reducedMotion = window.matchMedia?.(
            "(prefers-reduced-motion: reduce)",
        ).matches;
        if (
            !canRotate ||
            interactionPaused ||
            manuallyPaused ||
            reducedMotion
        ) {
            return;
        }

        const timer = window.setTimeout(() => {
            const currentIndex = games.findIndex(
                (game) => game.gameId === selectedGame?.gameId,
            );
            const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % games.length;
            onSelect(games[nextIndex]);
        }, 5000);

        return () => window.clearTimeout(timer);
    }, [
        canRotate,
        games,
        interactionPaused,
        manuallyPaused,
        onSelect,
        selectedGame?.gameId,
    ]);

    return (
        <section className="relative isolate mx-auto min-h-[580px] w-full max-w-[var(--width-shell)] overflow-hidden text-white max-sm:min-h-[600px]">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
                alt=""
                aria-hidden="true"
                className="absolute inset-0 -z-20 size-full object-cover object-[center_52%] max-sm:object-[58%_center]"
                src={STADIUM_IMAGE_URL}
            />
            <div
                aria-hidden="true"
                className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,rgba(6,9,15,0.9)_0%,rgba(6,9,15,0.74)_48%,rgba(6,9,15,0.46)_100%)] max-[1024px]:bg-[linear-gradient(180deg,rgba(6,9,15,0.82)_0%,rgba(6,9,15,0.66)_100%)]"
            />

            <div className="grid min-h-[580px] grid-cols-[minmax(0,1fr)_minmax(340px,0.8fr)] items-center gap-12 px-[7vw] py-14 max-lg:grid-cols-1 max-lg:content-center max-lg:gap-8 max-sm:min-h-[600px] max-sm:px-4 max-sm:py-10">
                <div className="min-w-0 max-w-[720px]">
                    <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
                        2026 KBO LEAGUE
                    </span>
                    <h1 className="mt-[15px] mb-5 max-w-[640px] text-[clamp(48px,5.8vw,82px)] leading-[1.02] font-black tracking-normal max-sm:text-[48px]">
                        지금 바로
                        <br/>
                        <em className="text-brand not-italic">예매하세요</em>
                    </h1>
                    <p className="mb-7 text-sm leading-[1.75] text-white/75">
                        KBO 리그 전 구단 홈 경기를 확인하고
                        <br/>
                        공정한 대기열을 통해 원하는 좌석을 선택하세요.
                    </p>
                </div>

                {selectedGame && (
                    <div
                        aria-label="선택 경기"
                        className="grid w-full max-w-[460px] gap-6 rounded-panel border border-white/20 bg-black/50 px-6 py-6 shadow-card backdrop-blur-md lg:col-start-2 lg:justify-self-end"
                        key={selectedGame.gameId}
                        onBlurCapture={() => setInteractionPaused(false)}
                        onFocusCapture={() => setInteractionPaused(true)}
                        onMouseEnter={() => setInteractionPaused(true)}
                        onMouseLeave={() => setInteractionPaused(false)}
                        role="group"
                    >
                        <div className="flex items-center justify-between gap-4">
                            <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
                                TODAY&apos;S GAME · {selectedMeta?.label}
                            </span>
                            {canRotate && (
                                <Button
                                    aria-label={
                                        manuallyPaused
                                            ? "경기 자동 전환 재생"
                                            : "경기 자동 전환 일시정지"
                                    }
                                    className="rounded-full border-white/20 text-white hover:border-white/40 hover:bg-white/10"
                                    onClick={() => setManuallyPaused((paused) => !paused)}
                                    size="icon-sm"
                                    type="button"
                                    variant="ghost"
                                >
                                    {manuallyPaused ? (
                                        <Play aria-hidden="true"/>
                                    ) : (
                                        <Pause aria-hidden="true"/>
                                    )}
                                </Button>
                            )}
                        </div>
                        <div className="grid min-w-0 gap-2">
                            <strong className="text-xl leading-snug">
                                {selectedGame.title}
                            </strong>
                            <small className="text-sm text-white/70">
                                {formatGameDate(selectedGame.gameAt)} ·{" "}
                                {selectedGame.stadium.name}
                            </small>
                        </div>
                        <Button
                            className="w-full"
                            disabled={
                                bookingBusy ||
                                selectedGame.bookingStatus !== "OPEN" ||
                                (authenticated && selectedCompleted)
                            }
                            onClick={() => onStartBooking(selectedGame)}
                            type="button"
                        >
                            {selectedCompleted
                                ? "예매 완료"
                                : selectedGame.bookingStatus === "OPEN"
                                  ? "예매하기"
                                  : selectedMeta?.action}
                            <ArrowRight aria-hidden="true"/>
                        </Button>
                        {canRotate && (
                            <div
                                aria-label="오늘의 경기 선택"
                                className="flex items-center justify-center gap-2"
                                role="navigation"
                            >
                                {games.map((game, index) => {
                                    const selected =
                                        game.gameId === selectedGame.gameId;

                                    return (
                                        <button
                                            aria-current={selected}
                                            aria-label={`${index + 1}번째 경기 보기`}
                                            className={`size-2.5 rounded-full border border-white/50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/80 ${
                                                selected
                                                    ? "bg-brand"
                                                    : "bg-white/30 hover:bg-white/70"
                                            }`}
                                            key={game.gameId}
                                            onClick={() => onSelect(game)}
                                            onFocus={() => onSelect(game)}
                                            onMouseEnter={() => onSelect(game)}
                                            type="button"
                                        />
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </section>
    );
}
