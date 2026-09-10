import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {getGame} from "@/api/games";
import QueuePage from "@/app/(booking)/games/[gameId]/queue/page";

const mocks = vi.hoisted(() => ({
    auth: {
        isAuthed: true,
    },
    useQueue: vi.fn(),
    resume: {
        restoring: true,
        shouldEnterQueue: false,
        error: null as string | null,
    },
}));

vi.mock("next/navigation", () => ({
    useParams: () => ({gameId: "111"}),
    useRouter: () => ({push: vi.fn()}),
}));

vi.mock("@/api/games", () => ({getGame: vi.fn()}));
vi.mock("@/api/queues", () => ({getQueueStatus: vi.fn()}));
vi.mock("@/hooks/use-auth", () => ({
    useAuth: () => mocks.auth,
}));
vi.mock("@/hooks/use-booking-resume", () => ({
    useBookingResume: () => mocks.resume,
}));
vi.mock("@/hooks/use-queue", () => ({
    useQueue: (...args: unknown[]) => mocks.useQueue(...args),
}));
vi.mock("@/components/queue/queue-screen", () => ({
    QueueScreen: ({error}: { error: string | null }) => (
        <div>{error ?? "queue-screen"}</div>
    ),
}));

function renderPage() {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}},
    });
    return render(
        <QueryClientProvider client={queryClient}>
            <QueuePage/>
        </QueryClientProvider>,
    );
}

describe("대기열 페이지", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(getGame).mockImplementation(() => new Promise(() => undefined));
        Object.assign(mocks.resume, {
            restoring: true,
            shouldEnterQueue: false,
            error: null,
        });
        mocks.auth.isAuthed = true;
        mocks.useQueue.mockReturnValue({
            queue: null,
            initialRank: null,
            error: null,
            cancel: vi.fn(),
        });
    });

    afterEach(cleanup);

    it("이전 예매 단계를 확인하는 동안 새 대기열 등록을 비활성화한다", () => {
        renderPage();

        expect(mocks.useQueue).toHaveBeenCalledWith(111, false);
    });

    it("복원할 단계가 없다고 확인된 뒤에만 새 대기열 등록을 활성화한다", () => {
        Object.assign(mocks.resume, {
            restoring: false,
            shouldEnterQueue: true,
        });

        renderPage();

        expect(mocks.useQueue).toHaveBeenCalledWith(111, true);
    });

    it("진행 상태 확인 오류를 대기열 화면에 표시한다", () => {
        Object.assign(mocks.resume, {
            restoring: false,
            error: "예매 상태를 확인하지 못했습니다.",
        });

        renderPage();

        expect(
            screen.getByText("예매 상태를 확인하지 못했습니다."),
        ).toBeInTheDocument();
    });

    it("비로그인 직접 접근은 대기열을 시작하지 않고 홈 이동을 안내한다", () => {
        mocks.auth.isAuthed = false;

        renderPage();

        expect(
            screen.getByRole("heading", {name: "비정상적인 접근입니다"}),
        ).toBeInTheDocument();
        expect(screen.getByRole("link", {name: "홈으로 이동"})).toHaveAttribute(
            "href",
            "/",
        );
        expect(mocks.useQueue).not.toHaveBeenCalled();
    });
});
