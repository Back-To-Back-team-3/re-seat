import {cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {AdminGameManagement} from "@/components/admin/games/admin-game-management";
import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import type {GameSummary} from "@/types/game";

const adminGames = vi.hoisted(() => ({
    games: [] as GameSummary[],
    isLoading: false,
    error: null as Error | null,
    updateStatus: vi.fn(),
    openInventory: vi.fn(),
    isUpdatingStatus: false,
    isOpeningInventory: false,
}));

vi.mock("@/hooks/use-admin-games", () => ({
    useAdminGames: () => adminGames,
}));

afterEach(cleanup);

const game: GameSummary = {
    gameId: 111,
    title: "LG 트윈스 vs 두산 베어스",
    homeTeam: {teamId: 1, name: "LG 트윈스"},
    awayTeam: {teamId: 2, name: "두산 베어스"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2026-09-12 18:30:00",
    bookingOpenAt: "2026-09-01 10:00:00",
    bookingCloseAt: "2026-09-12 17:30:00",
    bookingStatus: "OPEN",
};

describe("관리자 경기 관리", () => {
    it("최신 경기부터 10개씩 나누어 표시한다", () => {
        adminGames.games = Array.from({length: 12}, (_, index) => ({
            ...game,
            gameId: index + 1,
            title: `경기 ${index + 1}`,
            gameAt: `2026-09-${String(index + 1).padStart(2, "0")} 18:30:00`,
        }));

        render(<AdminGameManagement/>);

        const firstPageRows = within(screen.getByRole("table")).getAllByRole("row").slice(1);
        expect(firstPageRows).toHaveLength(10);
        expect(within(firstPageRows[0]).getByRole("button")).toHaveTextContent("경기 12");
        expect(within(firstPageRows[9]).getByRole("button")).toHaveTextContent("경기 3");

        fireEvent.click(screen.getByRole("button", {name: "다음 페이지"}));

        const secondPageRows = within(screen.getByRole("table")).getAllByRole("row").slice(1);
        expect(secondPageRows).toHaveLength(2);
        expect(within(secondPageRows[0]).getByRole("button")).toHaveTextContent("경기 2");
        expect(screen.getByText("2 / 2 페이지")).toBeInTheDocument();
    });

    it("선택한 경기의 운영 작업을 목록보다 먼저 표시한다", () => {
        adminGames.games = [game];
        render(<AdminGameManagement/>);

        fireEvent.click(screen.getByRole("button", {name: /경기 선택 LG 트윈스/}));

        const operations = screen.getByText("SELECTED GAME").closest("section");
        const table = screen.getByRole("table");
        expect(operations).not.toBeNull();
        expect(operations!.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it("목록에서 선택한 경기 ID를 전달한다", () => {
        const onSelect = vi.fn();
        render(<AdminGameList games={[game]} onSelect={onSelect} selectedGameId={null}/>);

        fireEvent.click(screen.getByRole("button", {name: /LG 트윈스 vs 두산 베어스/}));

        expect(onSelect).toHaveBeenCalledWith(111);
    });

    it("변경할 예매 상태와 사유를 전달한다", () => {
        const onUpdateStatus = vi.fn();
        render(
            <AdminGameOperations
                game={game}
                isOpeningInventory={false}
                isUpdatingStatus={false}
                onOpenInventory={vi.fn()}
                onUpdateStatus={onUpdateStatus}
            />,
        );

        fireEvent.change(screen.getByLabelText("변경할 예매 상태"), {target: {value: "CLOSED"}});
        fireEvent.change(screen.getByLabelText("상태 변경 사유"), {target: {value: "판매 종료"}});
        fireEvent.click(screen.getByRole("button", {name: "예매 상태 변경"}));

        expect(onUpdateStatus).toHaveBeenCalledWith("CLOSED", "판매 종료");
    });
});
