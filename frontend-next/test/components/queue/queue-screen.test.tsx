import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { QueueScreen } from "@/components/queue/queue-screen";
import type { QueueViewState } from "@/types/game";

const BASE_QUEUE: QueueViewState = {
  gameId: 1,
  rank: 10,
  estimatedWaitSeconds: 30,
  queueStatus: "WAITING",
  admitted: false,
  registrationPending: false,
  queueToken: null,
  tokenExpiresAt: null,
};

describe("대기열 화면", () => {
  afterEach(cleanup);

  it("현재 순위와 예상 대기 시간을 표시한다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={BASE_QUEUE}
      />,
    );

    expect(screen.getByText("10번째")).toBeInTheDocument();
    expect(screen.getByText(/30초/)).toBeInTheDocument();
  });

  it("대기열 등록 중에는 등록 중 문구와 접수 중 상태를 보여준다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={null}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={{ ...BASE_QUEUE, rank: 0, registrationPending: true }}
      />,
    );

    expect(screen.getByText("대기열 등록 중입니다.")).toBeInTheDocument();
    expect(screen.getByText("접수 중")).toBeInTheDocument();
  });

  it("등록이 끝나면 접속 인원이 많다는 문구와 순번을 보여준다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={BASE_QUEUE}
      />,
    );

    expect(
      screen.getByText("접속 인원이 많아 대기 중입니다."),
    ).toBeInTheDocument();
  });

  it("입장 토큰이 발급되면 완료 문구와 입장 가능 상태를 보여준다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={{
          ...BASE_QUEUE,
          rank: 0,
          admitted: true,
          queueStatus: "ADMITTED",
          queueToken: "token-1",
          tokenExpiresAt: "2026-08-08T00:00:00+09:00",
        }}
      />,
    );

    expect(screen.getByText("대기가 완료되었습니다.")).toBeInTheDocument();
    expect(screen.getByText("입장 가능")).toBeInTheDocument();
  });

  it("예상 대기시간을 계산 중이면 계산 중이라고 표시한다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={{ ...BASE_QUEUE, estimatedWaitSeconds: null }}
      />,
    );

    expect(screen.getByText("계산 중")).toBeInTheDocument();
  });

  it("대기 중에는 취소 버튼을 보여주고 좌석 이동 버튼은 비활성화한다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={BASE_QUEUE}
      />,
    );

    expect(
      screen.getByRole("button", { name: "예매 취소하고 돌아가기" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "좌석 선택으로 이동 →" }),
    ).toBeDisabled();
  });

  it("입장이 완료되면 취소 버튼은 사라지고 좌석 이동 버튼이 활성화된다", () => {
    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={{
          ...BASE_QUEUE,
          rank: 0,
          admitted: true,
          queueStatus: "ADMITTED",
          queueToken: "token-1",
          tokenExpiresAt: "2026-08-08T00:00:00+09:00",
        }}
      />,
    );

    expect(
      screen.queryByRole("button", { name: "예매 취소하고 돌아가기" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "좌석 선택으로 이동 →" }),
    ).toBeEnabled();
  });

  it("상태 확인 버튼을 누르면 새로고침을 요청한다", () => {
    const onRefresh = vi.fn();

    render(
      <QueueScreen
        busy={false}
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={onRefresh}
        queue={BASE_QUEUE}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "상태 확인" }));

    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("busy 상태에서는 상태 확인 버튼도 비활성화한다", () => {
    render(
      <QueueScreen
        busy
        error={null}
        initialRank={20}
        onCancel={vi.fn()}
        onContinue={vi.fn()}
        onRefresh={vi.fn()}
        queue={BASE_QUEUE}
      />,
    );

    expect(screen.getByRole("button", { name: "상태 확인" })).toBeDisabled();
  });
});
