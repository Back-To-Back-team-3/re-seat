"use client";

import {useRouter} from "next/navigation";
import {useState} from "react";

import {VerificationPanel} from "@/components/auth/verification-panel";
import {Alert} from "@/components/common/alert";
import {EmptyState} from "@/components/common/empty-state";
import {StadiumCongestionSection} from "@/components/congestion/stadium-congestion-section";
import {GAME_STATUS_META} from "@/components/games/game-card";
import {GameList} from "@/components/games/game-list";
import {TodayGamesPanel} from "@/components/games/today-games-panel";
import {useAuth} from "@/hooks/use-auth";
import {useGames} from "@/hooks/use-games";
import {getCompletedGameIds} from "@/lib/completed-games";
import {KST_TIME_ZONE, STADIUM_IMAGE_URL} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";



/**
 * KST(Asia/Seoul) 기준 오늘 날짜를 YYYY-MM-DD로 반환한다.
 *
 * 초기 선택 경기(chooseInitialGame)와 오늘의 경기 패널이 같은 "오늘" 정의를
 * 공유하도록 이 함수 하나만 사용한다. 브라우저 로컬 시간대가 달라도 백엔드
 * 경기 일시의 기준(KST)과 항상 일치시키기 위함이다.
 */
function getKstToday() {
    return new Date().toLocaleDateString("sv-SE", {timeZone: KST_TIME_ZONE});
}

function chooseInitialGame(games: GameSummary[]) {
    const today = getKstToday();

    return (
        games.find(
            (game) =>
                game.gameAt.startsWith(today) && game.bookingStatus === "OPEN",
        ) ??
        games.find((game) => game.gameAt.startsWith(today)) ??
        games.find((game) => game.bookingStatus === "OPEN") ??
        games[0] ??
        null
    );
}

/**
 * 인증 상태와 경기 서버 상태를 결합해 기존 홈 경기 예매 화면을 구성합니다.
 *
 * OAuth 콜백이 돌아오는 `/`와 계획된 `/games`가 같은 화면을 사용하므로 페이지
 * 컴포넌트를 공유합니다. 선택한 경기 ID는 이 화면에만 필요한 임시 상태이며,
 * 예매를 시작하면 gameId를 URL에 넣어 대기열 라우트로 전달합니다.
 */
export function GamesPage() {
    const router = useRouter();
    const auth = useAuth();
    const gamesQuery = useGames();
    const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
    const [verificationError, setVerificationError] = useState<string | null>(
        null,
    );
    const games = gamesQuery.data ?? [];
    const selectedGame =
        games.find((game) => game.gameId === selectedGameId) ??
        chooseInitialGame(games);
    const message =
        verificationError ?? gamesQuery.error?.message ?? auth.message;
    const today = getKstToday();
    const todayGames = games.filter((game) => game.gameAt.startsWith(today));
    const selectedMeta = selectedGame
        ? GAME_STATUS_META[selectedGame.bookingStatus]
        : null;
    const completedGameIds = getCompletedGameIds();
    const selectedCompleted = selectedGame
        ? completedGameIds.has(selectedGame.gameId)
        : false;

    function startBooking(game: GameSummary) {
        if (!auth.isAuthed) {
            auth.login();
            return;
        }

        router.push(`/games/${game.gameId}/queue`);
    }

    function selectGame(game: GameSummary) {
        setSelectedGameId(game.gameId);
    }

    return (
        <>
            {message && (
                <Alert
                    message={message}
                    onClose={() => {
                        setVerificationError(null);
                        auth.dismissMessage();
                    }}
                    variant={
                        verificationError || gamesQuery.error
                            ? "error"
                            : auth.messageVariant
                    }
                />
            )}

            {auth.isAuthed && !auth.isVerified ? (
                <VerificationPanel
                    busy={auth.busy}
                    onError={setVerificationError}
                    onLogout={auth.logout}
                    onVerify={auth.verify}
                />
            ) : (
                <>
                    {/*
            Vite의 .home-page는 패딩이 없고 hero-banner/schedule-section이 각자
            7vw 여백을 관리한다. 아래 GameList용 <main>은 5vw 패딩을 이미 갖고
            있어 그대로 두고, 히어로만 별도 <section>으로 shell 폭에 맞춰 분리했다.
          */}
                    <section
                        className="relative mx-auto grid w-full max-w-[var(--width-shell)] min-h-[660px] grid-cols-[minmax(0,1.08fr)_minmax(390px,0.92fr)] gap-[34px] overflow-hidden bg-[radial-gradient(circle_at_78%_38%,rgba(224,53,53,0.1),transparent_34%)] pr-[7vw] max-[1024px]:grid-cols-1 max-[1024px]:pr-0">
                        <div
                            className="z-[2] self-center pt-[70px] pb-[58px] pl-[7vw] max-[900px]:pr-[7vw] max-[900px]:pb-[35px]">
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
                                <div
                                    className="grid min-w-[86px] gap-px border-r border-border pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                                    <strong className="font-mono text-xl">10개</strong>
                                    <span className="text-xs text-muted-foreground">구단</span>
                                </div>
                                <div
                                    className="grid min-w-[86px] gap-px border-r border-border pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                                    <strong className="font-mono text-xl">500</strong>
                                    <span className="text-xs text-muted-foreground">
                    데모 좌석/경기
                  </span>
                                </div>
                                <div className="grid min-w-[86px] gap-px pr-6 max-sm:min-w-0 max-sm:pr-[13px]">
                                    <strong className="font-mono text-xl">2석</strong>
                                    <span className="text-xs text-muted-foreground">
                    최대 선택
                  </span>
                                </div>
                            </div>
                            {selectedGame && (
                                <div
                                    className="grid w-[min(590px,calc(100vw-40px))] grid-cols-[1fr_auto] items-center gap-5 rounded-panel border border-border bg-surface/90 px-[18px] py-[17px] shadow-card backdrop-blur-md max-sm:grid-cols-1">
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
                                    <button
                                        className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-3 rounded-control border-0 bg-brand px-[22px] font-extrabold text-white shadow-[0_8px_20px_rgba(224,53,53,0.2)] disabled:cursor-not-allowed max-sm:w-full"
                                        disabled={
                                            auth.busy ||
                                            selectedGame.bookingStatus !== "OPEN" ||
                                            (auth.isAuthed && selectedCompleted)
                                        }
                                        onClick={() => startBooking(selectedGame)}
                                        type="button"
                                    >
                                        {selectedCompleted
                                            ? "예매 완료"
                                            : auth.isAuthed
                                                ? selectedMeta?.action
                                                : "로그인 후 예매"}
                                        <span aria-hidden="true">→</span>
                                    </button>
                                </div>
                            )}
                        </div>
                        <div
                            className="grid min-w-0 content-center gap-[18px] py-12 max-[1024px]:px-[7vw] max-[1024px]:pt-0 max-[1024px]:pb-[52px] max-sm:px-4 max-sm:pb-[38px]">
                            <TodayGamesPanel
                                games={todayGames}
                                onSelect={selectGame}
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

                    <main className="mx-auto grid w-full max-w-[1440px] gap-10 px-[5vw] py-14 max-sm:px-4">
                        {gamesQuery.isLoading ? (
                            <p className="py-16 text-center text-muted-foreground">
                                경기 일정을 불러오고 있습니다.
                            </p>
                        ) : gamesQuery.error ? (
                            <div className="grid gap-4">
                                <EmptyState
                                    description={gamesQuery.error.message}
                                    title="경기 일정을 불러오지 못했습니다."
                                />
                                <button
                                    className="justify-self-center rounded-control border border-border bg-surface px-5 py-2 text-sm font-bold cursor-pointer"
                                    onClick={() => {
                                        void gamesQuery.refetch();
                                    }}
                                    type="button"
                                >
                                    일정 다시 불러오기
                                </button>
                            </div>
                        ) : (
                            <GameList
                                completedGameIds={completedGameIds}
                                games={games}
                                onReload={() => {
                                    void gamesQuery.refetch();
                                }}
                                onSelect={selectGame}
                                reloading={gamesQuery.isFetching}
                                selectedGameId={selectedGame?.gameId ?? null}
                            />
                        )}

                        {/* 경기장 주변 실시간 구역별 혼잡도 안내 (좌측 리스트 + 우측 지도) */}
                        <StadiumCongestionSection stadiumNum={1} />
                    </main>
                </>
            )}
        </>
    );
}
