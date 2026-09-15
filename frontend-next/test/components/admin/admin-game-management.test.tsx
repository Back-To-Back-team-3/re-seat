import {cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {AdminGameManagement} from "@/components/admin/games/admin-game-management";
import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import type {GameSummary} from "@/types/game";

const adminGames = vi.hoisted(() => ({
    games: [] as GameSummary[],
    page: {
        content: [] as GameSummary[], pageNumber: 0, pageSize: 10,
        totalElements: 0, totalPages: 0, isFirst: true, isLast: true,
    },
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
    it("서버에서 받은 경기 페이지와 전체 건수를 표시한다", () => {
        adminGames.games = [game];
        adminGames.page = {
            content: [game], pageNumber: 0, pageSize: 10,
            totalElements: 12, totalPages: 2, isFirst: true, isLast: false,
        };

        render(<AdminGameManagement/>);

        expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(2);
        expect(screen.getByText("총 12경기")).toBeInTheDocument();
        expect(screen.getByText("1 / 2 페이지")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "다음 페이지"})).toBeEnabled();
    });

    it("마지막 페이지에서는 다음 페이지 버튼을 비활성화한다", () => {
        adminGames.games = [game];
        adminGames.page = {
            content: [game], pageNumber: 0, pageSize: 10,
            totalElements: 1, totalPages: 1, isFirst: true, isLast: false,
        };

        render(<AdminGameManagement/>);

        expect(screen.getByText("1 / 1 페이지")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "다음 페이지"})).toBeDisabled();
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
