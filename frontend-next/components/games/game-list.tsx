"use client";

import { useMemo, useState } from "react";

import { EmptyState } from "@/components/common/empty-state";
import { GameCalendar } from "@/components/games/game-calendar";
import {
  GameCard,
  GAME_STATUS_META,
} from "@/components/games/game-card";
import { KST_TIME_ZONE } from "@/lib/constants";
import { formatGameDate } from "@/lib/date";
import type { GameSummary } from "@/types/game";

type GameListProps = {
  games: GameSummary[];
  selectedGameId: number | null;
  onSelect: (game: GameSummary) => void;
  onBook: (game: GameSummary) => void;
};

/**
 * 경기 선택 요약, 날짜·상태 필터와 시간순 경기 카드를 함께 관리합니다.
 *
 * 필터 값은 이 화면을 벗어나면 버려지는 UI 상태이므로 전역 store에 올리지 않습니다.
 * 입력 목록이 정렬되지 않았더라도 항상 gameAt과 gameId 순으로 표시해 API 페이지가
 * 나뉘거나 응답 순서가 달라져도 기존 화면 순서를 유지합니다.
 */
export function GameList({
  games,
  selectedGameId,
  onSelect,
  onBook,
}: GameListProps) {
  const [selectedDate, setSelectedDate] = useState<string | null>(() => {
    /*
     * 백엔드의 경기 일시는 KST를 기준으로 저장됩니다. 브라우저가 다른 시간대에
     * 있어도 기존 화면과 같은 날짜가 선택되도록 로컬 시간이 아닌 KST 날짜를
     * 초기값으로 사용합니다.
     */
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: KST_TIME_ZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  });
  const [status, setStatus] = useState<
    GameSummary["bookingStatus"] | "ALL"
  >("ALL");
  const [teamId, setTeamId] = useState<number | "ALL">("ALL");
  const [stadiumId, setStadiumId] = useState<number | "ALL">("ALL");
  const selectedGame =
    games.find((game) => game.gameId === selectedGameId) ?? null;
  const teams = useMemo(() => {
    const entries = new Map<number, string>();
    games.forEach((game) => {
      entries.set(game.homeTeam.teamId, game.homeTeam.name);
      entries.set(game.awayTeam.teamId, game.awayTeam.name);
    });
    return [...entries.entries()].sort((left, right) =>
      left[1].localeCompare(right[1], "ko"),
    );
  }, [games]);
  const stadiums = useMemo(() => {
    const entries = new Map<number, string>();
    games.forEach((game) =>
      entries.set(game.stadium.stadiumId, game.stadium.name),
    );
    return [...entries.entries()].sort((left, right) =>
      left[1].localeCompare(right[1], "ko"),
    );
  }, [games]);
  const calendarGames = useMemo(
    () =>
      games.filter(
        (game) =>
          (status === "ALL" || game.bookingStatus === status) &&
          (teamId === "ALL" ||
            game.homeTeam.teamId === teamId ||
            game.awayTeam.teamId === teamId) &&
          (stadiumId === "ALL" || game.stadium.stadiumId === stadiumId),
      ),
    [games, stadiumId, status, teamId],
  );
  const filteredGames = useMemo(
    () =>
      calendarGames
        .filter(
          (game) => !selectedDate || game.gameAt.startsWith(selectedDate),
        )
        .slice()
        .sort(
          (left, right) =>
            left.gameAt.localeCompare(right.gameAt) ||
            left.gameId - right.gameId,
        ),
    [calendarGames, selectedDate],
  );

  return (
    <div className="grid gap-6">
      {selectedGame && (
        <section className="flex flex-wrap items-center justify-between gap-4 rounded-panel bg-foreground p-6 text-surface">
          <div>
            <span className="text-xs font-bold tracking-widest text-brand">
              SELECTED GAME ·{" "}
              {GAME_STATUS_META[selectedGame.bookingStatus].label}
            </span>
            <strong className="mt-2 block text-xl">
              {selectedGame.title}
            </strong>
            <small className="text-surface/70">
              {formatGameDate(selectedGame.gameAt)} ·{" "}
              {selectedGame.stadium.name}
            </small>
          </div>
          <button
            className="cursor-pointer rounded-control border-0 bg-brand px-6 py-3 font-bold text-white disabled:cursor-not-allowed disabled:bg-muted"
            disabled={selectedGame.bookingStatus !== "OPEN"}
            onClick={() => onBook(selectedGame)}
            type="button"
          >
            {GAME_STATUS_META[selectedGame.bookingStatus].action}
          </button>
        </section>
      )}

      <GameCalendar
        games={calendarGames}
        onSelectDate={setSelectedDate}
        selectedDate={selectedDate}
      />

      <div className="flex items-end justify-between gap-4">
        <div>
          <strong className="block text-xl">
            {selectedDate
              ? `${selectedDate.replaceAll("-", ".")} 경기`
              : "전체 경기"}
          </strong>
          <span className="text-sm text-muted-foreground">
            {filteredGames.length}개 일정
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          {selectedDate && (
            <button
              className="cursor-pointer rounded-control border border-border bg-background px-3 py-2 text-sm font-bold text-foreground"
              onClick={() => setSelectedDate(null)}
              type="button"
            >
              날짜 선택 해제
            </button>
          )}
          <label className="grid gap-1 text-xs font-bold text-muted-foreground">
            구단
            <select
              className="rounded-control border border-border bg-surface px-3 py-2 text-sm text-foreground"
              onChange={(event) =>
                setTeamId(
                  event.target.value === "ALL"
                    ? "ALL"
                    : Number(event.target.value),
                )
              }
              value={teamId}
            >
              <option value="ALL">전체 구단</option>
              {teams.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label className="grid gap-1 text-xs font-bold text-muted-foreground">
            구장
            <select
              className="rounded-control border border-border bg-surface px-3 py-2 text-sm text-foreground"
              onChange={(event) =>
                setStadiumId(
                  event.target.value === "ALL"
                    ? "ALL"
                    : Number(event.target.value),
                )
              }
              value={stadiumId}
            >
              <option value="ALL">전체 구장</option>
              {stadiums.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label className="grid gap-1 text-xs font-bold text-muted-foreground">
            상태
            <select
              className="rounded-control border border-border bg-surface px-3 py-2 text-sm text-foreground"
              onChange={(event) =>
                setStatus(
                  event.target.value as
                    | GameSummary["bookingStatus"]
                    | "ALL",
                )
              }
              value={status}
            >
              <option value="ALL">전체 상태</option>
              {Object.entries(GAME_STATUS_META).map(([value, meta]) => (
                <option key={value} value={value}>
                  {meta.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {filteredGames.length === 0 ? (
        <EmptyState
          description="날짜 또는 상태를 변경해 다른 경기를 확인해주세요."
          title="조건에 맞는 경기가 없습니다."
        />
      ) : (
        <div className="grid gap-4">
          {filteredGames.map((game) => (
            <GameCard
              game={game}
              key={game.gameId}
              onBook={onBook}
              onSelect={onSelect}
              selected={selectedGameId === game.gameId}
            />
          ))}
        </div>
      )}
    </div>
  );
}
