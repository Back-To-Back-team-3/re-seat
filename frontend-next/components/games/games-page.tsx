"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { LoginPanel } from "@/components/auth/login-panel";
import { VerificationPanel } from "@/components/auth/verification-panel";
import { Alert } from "@/components/common/alert";
import { EmptyState } from "@/components/common/empty-state";
import { GameList } from "@/components/games/game-list";
import { useAuth } from "@/hooks/use-auth";
import { useGames } from "@/hooks/use-games";
import type { GameSummary } from "@/types/game";

function chooseInitialGame(games: GameSummary[]) {
  const today = new Date().toLocaleDateString("sv-SE", {
    timeZone: "Asia/Seoul",
  });

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

  function startBooking(game: GameSummary) {
    if (!auth.isAuthed) {
      auth.login();
      return;
    }

    router.push(`/games/${game.gameId}/queue`);
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-50 grid min-h-[70px] grid-cols-[auto_1fr_auto] items-center border-b border-border bg-background/92 px-[5vw] shadow-[0_1px_12px_rgb(0_0_0/4%)] backdrop-blur-2xl">
        <Link
          aria-label="Re:Seat 홈"
          className="text-[28px] font-black tracking-[-1.5px] text-foreground"
          href="/games"
        >
          Re:<span className="text-brand">Seat</span>
        </Link>
        <nav
          aria-label="주요 메뉴"
          className="flex h-[70px] justify-center gap-[30px]"
        >
          <Link
            className="relative grid place-items-center px-1 text-sm font-bold text-foreground after:absolute after:right-0 after:bottom-0 after:left-0 after:h-0.5 after:bg-brand after:content-['']"
            href="/games"
          >
            경기 예매
          </Link>
          <Link
            aria-disabled={!auth.isAuthed}
            className={`grid place-items-center px-1 text-sm font-bold ${
              auth.isAuthed
                ? "text-foreground"
                : "pointer-events-none text-muted-foreground opacity-50"
            }`}
            href="/tickets"
          >
            내 티켓
          </Link>
        </nav>
        <LoginPanel
          isAuthed={auth.isAuthed}
          onLogin={auth.login}
          onLogout={auth.logout}
          profile={auth.profile}
          role={auth.role}
        />
      </header>

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
        <main className="mx-auto grid w-full max-w-[1440px] gap-10 px-[5vw] py-14 max-sm:px-4">
          <section>
            <span className="text-sm font-bold tracking-widest text-brand">
              2026 KBO LEAGUE
            </span>
            <h1 className="mt-3 text-4xl font-black tracking-tight sm:text-6xl">
              지금 바로 <em className="not-italic text-brand">예매하세요</em>
            </h1>
            <p className="mt-4 max-w-2xl leading-7 text-muted-foreground">
              KBO 리그 전 구단 홈 경기를 확인하고 공정한 대기열을 통해 원하는
              좌석을 선택하세요.
            </p>
          </section>

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
                className="justify-self-center rounded-control border border-border bg-surface px-5 py-2 text-sm font-bold"
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
              games={games}
              onBook={startBooking}
              onSelect={(game) => setSelectedGameId(game.gameId)}
              selectedGameId={selectedGame?.gameId ?? null}
            />
          )}
        </main>
      )}
    </div>
  );
}
