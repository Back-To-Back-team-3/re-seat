import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import Home from "@/app/page";
import { clearAuthNotice } from "@/api/auth";
import { API_BASE_URL } from "@/api/client";
import { server } from "@/test/mocks/server";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

function renderHome() {
  // 테스트마다 독립된 QueryClient를 사용해 이전 테스트의 프로필 캐시가 섞이지 않게 한다.
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Home />
    </QueryClientProvider>,
  );
}

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

describe("인증과 사용자 프로필 흐름", () => {
  beforeEach(() => {
    localStorage.clear();
    clearAuthNotice();
    window.history.replaceState({}, "", "/");
    server.use(
      // 홈 화면이 공개 경기 목록도 함께 조회하므로 인증 테스트에는 빈 정상 응답을 제공한다.
      http.get(`${API_BASE_URL}/games`, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json({
          success: true,
          errorCode: null,
          message: "경기 목록 조회 성공",
          data: {
            content: [],
            pageNumber: page,
            pageSize: 100,
            totalElements: 0,
            totalPages: 1,
            isFirst: true,
            isLast: true,
          },
        });
      }),
    );
  });

  afterEach(() => {
    cleanup();
  });

  it("로그인하지 않은 사용자에게 카카오 로그인 동작을 표시한다", async () => {
    renderHome();

    expect(
      await screen.findByRole("button", { name: "카카오 로그인" }),
    ).toBeInTheDocument();
  });

  it("OAuth 콜백 토큰을 저장하고 프로필을 표시한다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/users/me`, () =>
        HttpResponse.json(profileResponse(true)),
      ),
    );
    window.history.replaceState(
      {},
      "",
      "/?accessToken=access-token&refreshToken=refresh-token&isVerified=true",
    );

    renderHome();

    expect(await screen.findByText("야구팬")).toBeInTheDocument();
    expect(localStorage.getItem("accessToken")).toBe("access-token");
    expect(localStorage.getItem("refreshToken")).toBe("refresh-token");
    expect(localStorage.getItem("isVerified")).toBe("true");
    expect(window.location.search).toBe("");
  });

  it("프로필 조회 오류를 공통 알림으로 표시한다", async () => {
    localStorage.setItem("accessToken", "access-token");
    server.use(
      http.get(`${API_BASE_URL}/users/me`, () =>
        HttpResponse.json(
          {
            success: false,
            errorCode: "USER_NOT_FOUND",
            message: "사용자 정보를 찾을 수 없습니다.",
            data: null,
          },
          { status: 404 },
        ),
      ),
    );

    renderHome();

    expect(
      await screen.findByText("사용자 정보를 찾을 수 없습니다."),
    ).toBeInTheDocument();
  });

  it("로그아웃하면 인증 저장소를 비우고 로그인 상태로 돌아간다", async () => {
    localStorage.setItem("accessToken", "access-token");
    localStorage.setItem("refreshToken", "refresh-token");
    localStorage.setItem("queueToken", "queue-token");
    localStorage.setItem("isVerified", "true");
    server.use(
      http.get(`${API_BASE_URL}/users/me`, () =>
        HttpResponse.json(profileResponse(true)),
      ),
    );

    renderHome();
    fireEvent.click(await screen.findByRole("button", { name: "로그아웃" }));

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "카카오 로그인" }),
      ).toBeInTheDocument();
    });
    expect(localStorage.getItem("accessToken")).toBeNull();
    expect(localStorage.getItem("refreshToken")).toBeNull();
    expect(localStorage.getItem("queueToken")).toBeNull();
    expect(localStorage.getItem("isVerified")).toBeNull();
  });

  it("본인인증이 필요한 사용자는 인증 화면을 먼저 표시한다", async () => {
    localStorage.setItem("accessToken", "access-token");
    server.use(
      http.get(`${API_BASE_URL}/users/me`, () =>
        HttpResponse.json(profileResponse(false)),
      ),
    );

    renderHome();

    expect(
      await screen.findByRole("heading", {
        name: /안전한 예매를 위한/,
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "본인인증 시작하기" }),
    ).toBeInTheDocument();
  });
});
