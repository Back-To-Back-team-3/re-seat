import {ArrowRight} from "lucide-react";

import {Button} from "@/components/ui/button";
import {STADIUM_IMAGE_URL} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import {GAME_STATUS_META} from "@/lib/game-status";
import type {GameSummary} from "@/types/game";

type GamesHeroProps = {
    selectedGame: GameSummary | null;
    authenticated: boolean;
    bookingBusy: boolean;
    selectedCompleted: boolean;
    onStartBooking: (game: GameSummary) => void;
};

/** 홈 화면에서 선택한 경기의 예매 진입 영역을 표시한다. */
export function GamesHero({
    selectedGame,
    authenticated,
    bookingBusy,
    selectedCompleted,
    onStartBooking,
}: GamesHeroProps) {
    const selectedMeta = selectedGame
        ? GAME_STATUS_META[selectedGame.bookingStatus]
        : null;

    return (
        <section className="relative isolate mx-auto min-h-[620px] w-full max-w-[var(--width-shell)] overflow-hidden text-white max-sm:min-h-[calc(100svh-72px)]">
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

            <div className="flex min-h-[620px] items-center px-[7vw] py-14 max-sm:min-h-[calc(100svh-72px)] max-sm:px-4 max-sm:py-10">
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
                    <div className="mb-8 flex gap-6 max-sm:gap-[13px]">
                        <div className="grid min-w-[86px] gap-px border-r border-white/25 pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                            <strong className="font-mono text-xl">10개</strong>
                            <span className="text-xs text-white/65">구단</span>
                        </div>
                        <div className="grid min-w-[86px] gap-px border-r border-white/25 pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                            <strong className="font-mono text-xl">500</strong>
                            <span className="text-xs text-white/65">
                                데모 좌석/경기
                            </span>
                        </div>
                        <div className="grid min-w-[86px] gap-px pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                            <strong className="font-mono text-xl">2석</strong>
                            <span className="text-xs text-white/65">최대 선택</span>
                        </div>
                    </div>
                    {selectedGame && (
                        <div
                            aria-label="선택 경기"
                            className="grid w-full max-w-[590px] grid-cols-[1fr_auto] items-center gap-5 rounded-panel border border-white/20 bg-black/45 px-[18px] py-[17px] shadow-card backdrop-blur-md max-sm:grid-cols-1"
                            role="group"
                        >
                            <div className="grid min-w-0 gap-[3px]">
                                <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
                                    SELECTED GAME · {selectedMeta?.label}
                                </span>
                                <strong className="truncate text-[15px]">
                                    {selectedGame.title}
                                </strong>
                                <small className="truncate text-xs text-white/65">
                                    {formatGameDate(selectedGame.gameAt)} ·{" "}
                                    {selectedGame.stadium.name}
                                </small>
                            </div>
                            <Button
                                className="max-sm:w-full"
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
                                    : authenticated
                                      ? selectedMeta?.action
                                      : "로그인 후 예매"}
                                <ArrowRight aria-hidden="true"/>
                            </Button>
                        </div>
                    )}
                </div>
            </div>
        </section>
    );
}
