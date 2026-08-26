import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, fireEvent, render, screen, waitFor,} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {clearAuthNotice} from "@/api/auth";
import {API_BASE_URL} from "@/api/client";
import {Header} from "@/components/layout/header";
import {server} from "@/test/mocks/server";

vi.mock("next/navigation", () => ({
    usePathname: () => "/games",
}));

function profileResponse(isVerified: boolean) {
    return {
        success: true,
        errorCode: null,
        message: "내 정보 조회 완료",
        data: {
            id: 1,
            email: "user@example.com",
            name: "테스트 사용자",
            nickname: "야구팬",
            phone: null,
            isVerified,
        },
    };
}

function renderHeader() {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}},
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <Header/>
        </QueryClientProvider>,
    );
}

describe("Header", () => {
    beforeEach(() => {
        localStorage.clear();
        document.documentElement.removeAttribute("data-theme");
        clearAuthNotice();
        window.history.replaceState({}, "", "/");
    });

    afterEach(() => {
        cleanup();
    });

    it("로그인하지 않은 사용자에게 카카오 로그인 버튼을 보여준다", async () => {
        renderHeader();

        expect(
            await screen.findByRole("button", {name: "카카오 로그인"}),
        ).toBeInTheDocument();
    });

    it("비로그인 상태에서는 내 티켓 링크가 비활성 표시된다", async () => {
        renderHeader();

        const ticketsLink = await screen.findByRole("link", {name: "내 티켓"});
        expect(ticketsLink).toHaveAttribute("aria-disabled", "true");
    });

    it("비로그인 상태에서는 내 티켓 링크를 키보드로 활성화할 수 없다", async () => {
        renderHeader();

        const ticketsLink = await screen.findByRole("link", {name: "내 티켓"});
        // tab 순서에서 제외되어 키보드로 포커스할 수 없어야 한다.
        expect(ticketsLink).toHaveAttribute("tabindex", "-1");

        // 링크의 Enter 키 활성화는 브라우저에서 click 이벤트로 전달되므로,
        // 이 이벤트가 막히는지 확인하면 키보드 활성화도 함께 막힌다.
        const clickEvent = new MouseEvent("click", {
            bubbles: true,
            cancelable: true,
        });
        ticketsLink.dispatchEvent(clickEvent);

        expect(clickEvent.defaultPrevented).toBe(true);
    });

    it("테마 버튼을 누르면 문서 루트 테마 속성이 바뀐다", async () => {
        renderHeader();

        const themeButton = await screen.findByRole("button", {
            name: "화면 테마 변경",
        });
        fireEvent.click(themeButton);

        expect(document.documentElement.dataset.theme).toBe("dark");
    });

    it("Re:Seat 로고는 경기 목록으로 이동한다", async () => {
        renderHeader();

        const homeLink = await screen.findByRole("link", {name: "Re:Seat 홈"});
        expect(homeLink).toHaveAttribute("href", "/games");
    });

    it("로그인한 사용자의 닉네임을 표시한다", async () => {
        localStorage.setItem("accessToken", "access-token");
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(profileResponse(true)),
            ),
        );

        renderHeader();

        expect(await screen.findByText("야구팬")).toBeInTheDocument();
    });

    it("OAuth 콜백으로 돌아온 사용자는 토큰 저장 후 알림 구독으로 프로필이 갱신된다", async () => {
        // localStorage를 미리 채우지 않고 콜백 쿼리 문자열만 둔 채 렌더링해,
        // consumeAuthCallback → emitAuthChange → useSyncExternalStore 구독이
        // 실제로 재렌더링을 일으키는 경로를 검증한다.
        window.history.replaceState(
            {},
            "",
            "/?accessToken=access-token&refreshToken=refresh-token&isVerified=true",
        );
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(profileResponse(true)),
            ),
        );

        renderHeader();

        expect(await screen.findByText("야구팬")).toBeInTheDocument();
        expect(localStorage.getItem("accessToken")).toBe("access-token");
        expect(window.location.search).toBe("");
    });

    it("로그아웃하면 인증 저장소를 비우고 로그인 버튼으로 돌아간다", async () => {
        localStorage.setItem("accessToken", "access-token");
        localStorage.setItem("refreshToken", "refresh-token");
        localStorage.setItem("queueToken", "queue-token");
        localStorage.setItem("isVerified", "true");
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(profileResponse(true)),
            ),
        );

        renderHeader();
        fireEvent.click(await screen.findByRole("button", {name: "로그아웃"}));

        await waitFor(() => {
            expect(
                screen.getByRole("button", {name: "카카오 로그인"}),
            ).toBeInTheDocument();
        });
        expect(localStorage.getItem("accessToken")).toBeNull();
        expect(localStorage.getItem("refreshToken")).toBeNull();
        expect(localStorage.getItem("queueToken")).toBeNull();
        expect(localStorage.getItem("isVerified")).toBeNull();
    });
});
