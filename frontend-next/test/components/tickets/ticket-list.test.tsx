import {cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {TicketList} from "@/components/tickets/ticket-list";
import type {GameSummary} from "@/types/game";

const GAMES: GameSummary[] = [
    {
        gameId: 1,
        title: "LG 트윈스 vs 두산 베어스",
        homeTeam: {teamId: 1, name: "LG 트윈스"},
        awayTeam: {teamId: 2, name: "두산 베어스"},
        stadium: {stadiumId: 1, name: "잠실야구장"},
        gameAt: "2026-08-08T18:00:00",
        bookingOpenAt: "2026-08-01T10:00:00",
        bookingCloseAt: "2026-08-08T17:00:00",
        bookingStatus: "OPEN",
    },
];

/**
 * TicketList가 요구하는 필수 props 중 새로고침 관련 값은 대부분의 테스트와
 * 무관하므로 기본값을 여기서 한 번에 채우고 필요한 테스트에서만 덮어쓴다.
 */
function renderTicketList(
    overrides: Partial<React.ComponentProps<typeof TicketList>> = {},
) {
    return render(
        <TicketList
            games={GAMES}
            onReload={vi.fn()}
            reloading={false}
            tickets={[]}
            {...overrides}
        />,
    );
}

describe("티켓 목록", () => {
    afterEach(cleanup);

    it("티켓 카드가 경기, 좌석, 티켓번호, QR, 상태를 함께 보여준다", () => {
        renderTicketList({
            tickets: [
                {
                    ticketId: 1,
                    ticketNo: "TICKET-1",
                    gameId: 1,
                    seat: "1루 응원석 3열 10번",
                    status: "ISSUED",
                    qrToken: "QR-TOKEN-1",
                    gameAt: "2026-08-08T18:00:00",
                },
            ],
        });

        expect(
            screen.getByRole("heading", {name: "LG 트윈스 vs 두산 베어스"}),
        ).toBeInTheDocument();
        expect(screen.getByText("1루 응원석 3열 10번")).toBeInTheDocument();
        expect(screen.getByText("TICKET-1")).toBeInTheDocument();
        expect(screen.getByText("QR-TOKEN-1")).toBeInTheDocument();
        expect(screen.getByText("ISSUED")).toBeInTheDocument();
    });

    it("경기 정보를 찾지 못하면 경기 번호로 대체 표시한다", () => {
        renderTicketList({
            games: [],
            tickets: [
                {
                    ticketId: 2,
                    ticketNo: "TICKET-2",
                    gameId: 99,
                    seat: "내야 1열 1번",
                    status: "USED",
                    qrToken: "QR-TOKEN-2",
                    gameAt: "2026-08-08T18:00:00",
                },
            ],
        });

        expect(
            screen.getByRole("heading", {name: "경기 #99"}),
        ).toBeInTheDocument();
    });

    it("보유한 티켓이 없으면 안내 문구를 보여준다", () => {
        renderTicketList({tickets: []});

        expect(screen.getByText("보유한 티켓이 없습니다.")).toBeInTheDocument();
        expect(
            screen.getByText(
                "경기 예매와 결제를 완료하면 이곳에 티켓이 표시됩니다.",
            ),
        ).toBeInTheDocument();
    });

    it("새로고침 버튼을 누르면 목록을 다시 불러온다", () => {
        const onReload = vi.fn();
        renderTicketList({onReload});

        const button = screen.getByRole("button", {name: "↻ 티켓 새로고침"});
        fireEvent.click(button);

        expect(onReload).toHaveBeenCalledTimes(1);
    });

    it("새로고침 중에는 버튼을 비활성화한다", () => {
        renderTicketList({reloading: true});

        expect(
            screen.getByRole("button", {name: "↻ 티켓 새로고침"}),
        ).toBeDisabled();
    });

    it("REFUND_FAILED 상태의 티켓에는 환불 재시도 버튼이 표시되고 재시도 핸들러가 호출된다", () => {
        const onRetryCancelTicket = vi.fn();
        renderTicketList({
            tickets: [
                {
                    ticketId: 10,
                    ticketNo: "TICKET-FAILED",
                    gameId: 1,
                    seat: "1루 응원석 3열 10번",
                    status: "REFUND_FAILED",
                    qrToken: "QR-TOKEN-FAILED",
                    gameAt: "2026-08-08T18:00:00",
                },
            ],
            onRetryCancelTicket,
        });

        const retryButton = screen.getByRole("button", {name: "환불 재시도"});
        expect(retryButton).toBeInTheDocument();

        fireEvent.click(retryButton);

        expect(
            screen.getByRole("heading", {name: "티켓 환불 재시도 요청"}),
        ).toBeInTheDocument();
        expect(
            screen.getByText(
                "* 환불이 정상 처리되면 예약 좌석이 반환되며, 결제 수단에 따라 1~3 영업일 내 환불 처리됩니다.",
            ),
        ).toBeInTheDocument();

        const dialog = screen.getByRole("dialog");
        const modalConfirmButton = within(dialog).getByRole("button", {name: "환불 재시도"});
        fireEvent.click(modalConfirmButton);

        expect(onRetryCancelTicket).toHaveBeenCalledWith(10);
    });

    it("환불 요청 모달에 수정된 좌석 반환 안내 문구가 표시된다", () => {
        const onCancelTicket = vi.fn();
        renderTicketList({
            tickets: [
                {
                    ticketId: 11,
                    ticketNo: "TICKET-ISSUED",
                    gameId: 1,
                    seat: "1루 응원석 3열 10번",
                    status: "ISSUED",
                    refundable: true,
                    qrToken: "QR-TOKEN-ISSUED",
                    gameAt: "2026-08-08T18:00:00",
                },
            ],
            onCancelTicket,
        });

        const cancelButton = screen.getByRole("button", {name: "환불 요청"});
        fireEvent.click(cancelButton);

        expect(
            screen.getByText(
                "* 환불이 정상 처리되면 예약 좌석이 반환되며, 결제 수단에 따라 1~3 영업일 내 환불 처리됩니다.",
            ),
        ).toBeInTheDocument();
    });
});
