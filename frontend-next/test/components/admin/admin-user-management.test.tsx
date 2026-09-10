import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

import {AdminUserFilters} from "@/components/admin/users/admin-user-filters";
import {AdminUserList} from "@/components/admin/users/admin-user-list";
import {AdminUserTickets} from "@/components/admin/users/admin-user-tickets";

const user = {
    id: 7,
    email: "fan@example.com",
    name: "야구팬",
    nickname: "응원단장",
    phone: "010-1234-5678",
    role: "USER" as const,
    status: "ACTIVE" as const,
    isVerified: true,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
};

describe("관리자 회원 관리", () => {
    it("입력한 이메일을 검색 조건으로 전달한다", () => {
        const onSearch = vi.fn();
        render(<AdminUserFilters onSearch={onSearch}/>);

        fireEvent.change(screen.getByLabelText("이메일"), {
            target: {value: "fan@example.com"},
        });
        fireEvent.click(screen.getByRole("button", {name: "조회"}));

        expect(onSearch).toHaveBeenCalledWith({email: "fan@example.com"});
    });

    it("목록에서 선택한 회원 ID를 전달한다", () => {
        const onSelect = vi.fn();
        render(<AdminUserList onSelect={onSelect} users={[user]}/>);

        fireEvent.click(screen.getByRole("button", {name: /fan@example.com/}));

        expect(onSelect).toHaveBeenCalledWith(7);
    });

    it("재접수 API가 없는 환불 실패 티켓은 직권 취소를 비활성화한다", () => {
        render(
            <AdminUserTickets
                isCanceling={false}
                onCancel={vi.fn()}
                tickets={[
                    {
                        ticketId: 4,
                        ticketNo: "TKT-DEMO-01",
                        status: "REFUND_FAILED",
                        qrToken: "QR-DEMO-01",
                        issuedAt: "2026-09-09 18:00:00",
                        usedAt: null,
                        canceledAt: null,
                        gameId: 111,
                        gameTitle: "LG 트윈스 vs 두산 베어스",
                        stadiumName: "잠실야구장",
                        homeTeamName: "LG 트윈스",
                        awayTeamName: "두산 베어스",
                        gameAt: "2026-09-12 18:30:00",
                        seat: "1루 A 1열 1번",
                        gameSeatId: 1,
                        zoneName: "1루 A",
                        seatBlock: "A",
                        seatRow: "1",
                        seatNumber: "1",
                    },
                ]}
            />,
        );

        expect(screen.getByRole("button", {name: "직권 취소"})).toBeDisabled();
    });
});
