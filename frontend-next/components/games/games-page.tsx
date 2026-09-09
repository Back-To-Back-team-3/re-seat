"use client";

import {useRouter} from "next/navigation";
import {useState} from "react";
import {RefreshCw} from "lucide-react";

import {VerificationPanel} from "@/components/auth/verification-panel";
import {Alert} from "@/components/common/alert";
import {EmptyState} from "@/components/common/empty-state";
import {StadiumCongestionSection} from "@/components/congestion/stadium-congestion-section";
import {GamesHero} from "@/components/games/games-hero";
import {GameList} from "@/components/games/game-list";
import {Button} from "@/components/ui/button";
import {useAuth} from "@/hooks/use-auth";
import {useGames} from "@/hooks/use-games";
import {useTickets} from "@/hooks/use-tickets";
import {getKstDateKey} from "@/lib/date";
import type {GameSummary} from "@/types/game";

function chooseInitialGame(games: GameSummary[]) {
    const today = getKstDateKey();

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
    const ticketsQuery = useTickets(auth.isAuthed && auth.isVerified);
    const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
    const [verificationError, setVerificationError] = useState<string | null>(
        null,
    );
    const games = gamesQuery.data ?? [];
    const selectedGame =
        games.find((game) => game.gameId === selectedGameId) ??
        chooseInitialGame(games);
    const message =
        verificationError ??
        gamesQuery.error?.message ??
        ticketsQuery.error?.message ??
        auth.message;
    const today = getKstDateKey();
    const todayGames = games.filter((game) => game.gameAt.startsWith(today));
    // 환불이 완료되지 않은 서버 티켓만 현재 사용자가 예매한 경기로 판단한다.
    const completedGameIds = new Set(
        (ticketsQuery.data ?? [])
            .filter((ticket) => ticket.status !== "REFUNDED")
            .map((ticket) => ticket.gameId),
    );
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
                    <GamesHero
                        authenticated={auth.isAuthed}
                        bookingBusy={
                            auth.busy ||
                            ticketsQuery.isLoading ||
                            Boolean(ticketsQuery.error)
                        }
                        onSelect={selectGame}
                        onStartBooking={startBooking}
                        selectedCompleted={selectedCompleted}
                        selectedGame={selectedGame}
                        todayGames={todayGames}
                    />

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
                                <Button
                                    className="justify-self-center"
                                    onClick={() => {
                                        void gamesQuery.refetch();
                                    }}
                                    size="sm"
                                    type="button"
                                    variant="outline"
                                >
                                    <RefreshCw aria-hidden="true"/>
                                    일정 다시 불러오기
                                </Button>
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
