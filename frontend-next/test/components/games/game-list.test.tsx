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

/**
 * GameList가 요구하는 필수 props 중 새로고침 관련 값은 대부분의 테스트와
 * 무관하므로 기본값을 여기서 한 번에 채우고 필요한 테스트에서만 덮어쓴다.
 */
function renderGameList(overrides: Partial<React.ComponentProps<typeof GameList>> = {}) {
  return render(
    <GameList
      games={games}
      completedGameIds={new Set()}
      onReload={vi.fn()}
      onSelect={vi.fn()}
      reloading={false}
      selectedGameId={null}
      {...overrides}
    />,
  );
}

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

    renderGameList({ games: [todayGame, tomorrowGame] });

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
    renderGameList();
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

  it("카드 하단의 경기 선택도 선택 경기만 변경한다", () => {
    const onSelect = vi.fn();

    renderGameList({ games: [games[1]], onSelect });
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    fireEvent.click(screen.getByRole("button", { name: /OPEN 경기 선택/ }));
    expect(onSelect).toHaveBeenCalledWith(games[1]);

    // Vite의 OPEN 상태 카드 버튼 문구는 "경기 선택"이다(App.tsx의 gameStatusMeta).
    fireEvent.click(screen.getByRole("button", { name: "경기 선택" }));
    expect(onSelect).toHaveBeenCalledTimes(2);
  });

  it("결제를 완료한 경기는 예매 완료로 표시하고 다시 선택하지 못하게 한다", () => {
    const openGame = games[1];

    renderGameList({
      completedGameIds: new Set([openGame.gameId]),
      games: [openGame],
    });
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    expect(screen.getAllByText("예매 완료")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "예매 완료" })).toBeDisabled();
  });

  it.each([
    ["SCHEDULED", "예매 준비 중"],
    ["CLOSED", "예매 종료"],
    ["CANCELLED", "경기 취소"],
  ] as const)("%s 경기는 예매 버튼을 비활성화한다", (status, label) => {
    const game = games.find((candidate) => candidate.bookingStatus === status)!;

    renderGameList({ games: [game] });
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    expect(screen.getByRole("button", { name: label })).toBeDisabled();
  });

  it("선택한 경기는 예매 상태와 관계없이 선택됨으로 표시한다", () => {
    const scheduledGame = games[0];

    renderGameList({
      games: [scheduledGame],
      selectedGameId: scheduledGame.gameId,
    });
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    expect(screen.getByRole("button", { name: "선택됨" })).toBeDisabled();
  });

  it("경기 카드는 날짜 블록과 경기 제목을 함께 보여준다", () => {
    const game: GameSummary = {
      ...games[1],
      gameAt: "2026-08-07T18:00:00",
      title: "두산 베어스 홈 개막전",
    };

    renderGameList({ games: [game] });
    fireEvent.click(
      screen.getByRole("button", { name: "날짜 선택 해제" }),
    );

    const article = screen.getByRole("article");
    expect(within(article).getByText("8월")).toBeInTheDocument();
    expect(within(article).getByText("07")).toBeInTheDocument();
    expect(within(article).getByText("금")).toBeInTheDocument();
    expect(
      within(article).getByText("두산 베어스 홈 개막전"),
    ).toBeInTheDocument();
  });

  it("새로고침 버튼을 누르면 일정을 다시 불러온다", () => {
    const onReload = vi.fn();
    renderGameList({ onReload });

    const button = screen.getByRole("button", { name: "↻ 일정 새로고침" });
    fireEvent.click(button);

    expect(onReload).toHaveBeenCalledTimes(1);
  });

  it("일정을 불러오는 동안에는 새로고침 버튼을 비활성화한다", () => {
    renderGameList({ reloading: true });

    expect(
      screen.getByRole("button", { name: "↻ 일정 새로고침" }),
    ).toBeDisabled();
  });

  it("예매 상태 범례를 표시한다", () => {
    renderGameList();

    const legend = screen.getByRole("list", { name: "예매 상태 범례" });
    expect(within(legend).getByText("예매 예정")).toBeInTheDocument();
    expect(within(legend).getByText("예매중")).toBeInTheDocument();
    expect(within(legend).getByText("예매 종료")).toBeInTheDocument();
    expect(within(legend).getByText("경기 취소")).toBeInTheDocument();
  });

  it("구단별·구장별·상태 필터를 제공한다", () => {
    renderGameList();

    expect(
      screen.getByRole("combobox", { name: "구단별" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: "구장별" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "상태" })).toBeInTheDocument();
  });
});
