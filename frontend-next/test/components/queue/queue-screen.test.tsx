import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { QueueScreen } from "@/components/queue/queue-screen";

describe("대기열 화면", () => {
  afterEach(cleanup);

  it("현재 순위와 예상 대기 시간을 표시한다", () => {
    render(
      <QueueScreen
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        queue={{
          gameId: 1,
          rank: 10,
          estimatedWaitSeconds: 30,
          queueStatus: "WAITING",
          admitted: false,
          registrationPending: false,
          queueToken: null,
          tokenExpiresAt: null,
        }}
      />,
    );

    expect(screen.getByText("10번째")).toBeInTheDocument();
    expect(screen.getByText(/30초/)).toBeInTheDocument();
  });
});
