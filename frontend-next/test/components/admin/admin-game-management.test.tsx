import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import type {GameSummary} from "@/types/game";

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
