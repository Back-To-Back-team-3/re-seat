import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { GameList } from "@/components/games/game-list";
import type { GameSummary } from "@/types/game";

const statuses: GameSummary["bookingStatus"][] = [
  "SCHEDULED",
  "OPEN",
  "CLOSED",
  "CANCELLED",
];

const games = statuses.map(
  (bookingStatus, index): GameSummary => ({
    gameId: index + 1,
    title: `${bookingStatus} 경기`,
    homeTeam: { teamId: 1, name: "홈팀" },
    awayTeam: { teamId: index + 2, name: `원정팀 ${index + 1}` },
    stadium: { stadiumId: 1, name: "테스트 구장" },
    // 입력 순서를 뒤집어도 화면에서는 경기 시각순으로 정렬되어야 한다.
    gameAt: `2026-08-${String(11 - index).padStart(2, "0")}T18:00:00`,
    bookingOpenAt: "2026-08-01T10:00:00",
    bookingCloseAt: "2026-08-12T17:00:00",
    bookingStatus,
  }),
);

describe("경기 목록", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("처음에는 KST 기준 오늘 경기만 표시한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-07T03:00:00Z"));
    const todayGame = {
      ...games[0],
      gameAt: "2026-08-07T18:00:00",
    };
    const tomorrowGame = {
      ...games[1],
      gameAt: "2026-08-08T18:00:00",
    };

    render(
      <GameList
        games={[todayGame, tomorrowGame]}
        onBook={vi.fn()}
        onSelect={vi.fn()}
        selectedGameId={null}
      />,
    );

    expect(screen.getByText("2026.08.07 경기")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByRole("article")).toHaveAttribute(
      "data-game-id",
      String(todayGame.gameId),
    );

    // 기존 화면처럼 사용자가 원할 때 날짜 조건을 해제해 전체 일정을 볼 수 있어야 한다.
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );
    expect(screen.getByText("전체 경기")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(2);
  });

  it("네 가지 예매 상태를 표시하고 경기 시각순으로 정렬한다", () => {
    render(
      <GameList
        games={games}
        onBook={vi.fn()}
        onSelect={vi.fn()}
        selectedGameId={null}
      />,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    const articles = screen.getAllByRole("article");
    expect(articles.map((article) => article.dataset.gameId)).toEqual([
      "4",
      "3",
      "2",
      "1",
    ]);
    expect(within(articles[0]).getAllByText("경기 취소")).not.toHaveLength(0);
    expect(within(articles[1]).getAllByText("예매 종료")).not.toHaveLength(0);
    expect(within(articles[2]).getByText("예매중")).toBeInTheDocument();
    expect(within(articles[3]).getByText("예매 예정")).toBeInTheDocument();
  });

  it("경기 선택과 예매 시작 동작을 구분한다", () => {
    const onSelect = vi.fn();
    const onBook = vi.fn();

    render(
      <GameList
        games={[games[1]]}
        onBook={onBook}
        onSelect={onSelect}
        selectedGameId={null}
      />,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    fireEvent.click(screen.getByRole("button", { name: /OPEN 경기 선택/ }));
    expect(onSelect).toHaveBeenCalledWith(games[1]);
    expect(onBook).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "예매 시작" }));
    expect(onBook).toHaveBeenCalledWith(games[1]);
  });

  it.each([
    ["SCHEDULED", "예매 준비 중"],
    ["CLOSED", "예매 종료"],
    ["CANCELLED", "경기 취소"],
  ] as const)("%s 경기는 예매 시작 버튼을 비활성화한다", (status, label) => {
    const game = games.find((candidate) => candidate.bookingStatus === status)!;

    render(
      <GameList
        games={[game]}
        onBook={vi.fn()}
        onSelect={vi.fn()}
        selectedGameId={null}
      />,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    expect(screen.getByRole("button", { name: label })).toBeDisabled();
  });

  it("선택한 경기는 예매 상태와 관계없이 선택됨으로 표시한다", () => {
    const scheduledGame = games[0];

    render(
      <GameList
        games={[scheduledGame]}
        onBook={vi.fn()}
        onSelect={vi.fn()}
        selectedGameId={scheduledGame.gameId}
      />,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    expect(screen.getByRole("button", { name: "선택됨" })).toBeDisabled();
  });
});
