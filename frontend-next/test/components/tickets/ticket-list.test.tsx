import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { TicketList } from "@/components/tickets/ticket-list";

describe("티켓 목록", () => {
  afterEach(cleanup);

  it("MOCK fallback임을 사용자에게 알리고 티켓 정보를 표시한다", () => {
    render(
      <TicketList
        source="mock"
        tickets={[
          {
            ticketId: -1,
            ticketNo: "MOCK-ORDER-1",
            gameId: 1,
            seat: "내야 1열 2번",
            status: "ISSUED",
            qrToken: "MOCK-QR-1-1",
            gameAt: "2026-08-08T18:00:00",
          },
        ]}
      />,
    );

    expect(screen.getByText(/임시 티켓/)).toBeInTheDocument();
    expect(screen.getByText("MOCK-ORDER-1")).toBeInTheDocument();
    expect(screen.getByText(/내야 1열 2번/)).toBeInTheDocument();
  });
});
