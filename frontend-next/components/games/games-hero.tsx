import {ArrowRight} from "lucide-react";

import {TodayGamesPanel} from "@/components/games/today-games-panel";
import {Button} from "@/components/ui/button";
import {STADIUM_IMAGE_URL} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import {GAME_STATUS_META} from "@/lib/game-status";
import type {GameSummary} from "@/types/game";

type GamesHeroProps = {
    selectedGame: GameSummary | null;
    todayGames: GameSummary[];
    authenticated: boolean;
    bookingBusy: boolean;
    selectedCompleted: boolean;
    onSelect: (game: GameSummary) => void;
    onStartBooking: (game: GameSummary) => void;
};

/** 선택 경기와 오늘의 일정을 조합해 홈 화면의 예매 진입 영역을 표시한다. */
export function GamesHero({
    selectedGame,
    todayGames,
    authenticated,
    bookingBusy,
    selectedCompleted,
    onSelect,
    onStartBooking,
}: GamesHeroProps) {
    const selectedMeta = selectedGame
        ? GAME_STATUS_META[selectedGame.bookingStatus]
        : null;

    return (
        <section className="relative mx-auto grid min-h-[660px] w-full max-w-[var(--width-shell)] grid-cols-[minmax(0,1.08fr)_minmax(390px,0.92fr)] gap-[34px] overflow-hidden bg-[radial-gradient(circle_at_78%_38%,rgba(224,53,53,0.1),transparent_34%)] pr-[7vw] max-[1024px]:grid-cols-1 max-[1024px]:pr-0">
            <div className="z-[2] min-w-0 self-center pt-[70px] pb-[58px] pl-[7vw] max-[900px]:pr-[7vw] max-[900px]:pb-[35px]">
                <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
                    2026 KBO LEAGUE
                </span>
                <h1 className="mt-[15px] mb-5 max-w-[640px] text-[clamp(48px,5.8vw,82px)] leading-[1.02] font-black tracking-[-0.055em] uppercase not-italic max-sm:text-[48px] max-sm:tracking-[-0.05em]">
                    지금 바로
                    <br/>
                    <em className="text-brand not-italic">예매하세요</em>
                </h1>
                <p className="mb-7 text-sm leading-[1.75] text-muted-foreground">
                    KBO 리그 전 구단 홈 경기를 확인하고
                    <br/>
                    공정한 대기열을 통해 원하는 좌석을 선택하세요.
                </p>
                <div className="mb-8 flex gap-6 max-sm:gap-[13px]">
                    <div className="grid min-w-[86px] gap-px border-r border-border pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                        <strong className="font-mono text-xl">10개</strong>
                        <span className="text-xs text-muted-foreground">구단</span>
                    </div>
                    <div className="grid min-w-[86px] gap-px border-r border-border pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                        <strong className="font-mono text-xl">500</strong>
                        <span className="text-xs text-muted-foreground">
                            데모 좌석/경기
                        </span>
                    </div>
                    <div className="grid min-w-[86px] gap-px pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                        <strong className="font-mono text-xl">2석</strong>
                        <span className="text-xs text-muted-foreground">최대 선택</span>
                    </div>
                </div>
                {selectedGame && (
                    <div
                        aria-label="선택 경기"
                        className="grid w-full max-w-[590px] grid-cols-[1fr_auto] items-center gap-5 rounded-panel border border-border bg-surface/90 px-[18px] py-[17px] shadow-card backdrop-blur-md max-sm:grid-cols-1"
                        role="group"
                    >
                        <div className="grid min-w-0 gap-[3px]">
                            <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
                                SELECTED GAME · {selectedMeta?.label}
                            </span>
                            <strong className="truncate text-[15px]">
                                {selectedGame.title}
                            </strong>
                            <small className="truncate text-xs text-muted-foreground">
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

            <div className="grid min-w-0 content-center gap-[18px] py-12 max-[1024px]:px-[7vw] max-[1024px]:pt-0 max-[1024px]:pb-[52px] max-sm:px-4 max-sm:pb-[38px]">
                <TodayGamesPanel
                    games={todayGames}
                    onSelect={onSelect}
                    selectedGameId={selectedGame?.gameId ?? null}
                />
                <figure className="relative m-0 h-[210px] overflow-hidden rounded-[18px] shadow-card after:absolute after:inset-0 after:bg-[linear-gradient(180deg,transparent_40%,rgba(9,13,21,0.7))] after:content-[''] max-sm:h-[170px]">
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
                        <span>SEOUL</span>
                    </figcaption>
                </figure>
            </div>
        </section>
    );
}
