import {delay, http, HttpResponse} from "msw";
import {afterEach, describe, expect, it, vi} from "vitest";

import {getAuthSnapshot, parseAuthSnapshot, subscribeAuth} from "@/api/auth";
import {apiRequest, AppError, unwrap} from "@/api/client";
import {storage} from "@/lib/storage";
import {server} from "@/test/mocks/server";

const API_BASE_URL = "http://localhost:8080/api/v1";
let unsubscribeAuth: (() => void) | null = null;

afterEach(() => {
    unsubscribeAuth?.();
    unsubscribeAuth = null;

    // 인증 상태가 다음 테스트에 남으면 요청 헤더와 401 시나리오가 달라질 수 있다.
    window.localStorage.clear();
});

describe("apiRequest", () => {
    it("인증 토큰과 대기열 토큰을 요청 헤더에 전달한다", async () => {
        // 실제 로그인과 대기열 입장 이후처럼 두 토큰이 저장된 상태를 준비한다.
        storage.local.set("accessToken", "access-token");
        storage.local.set("queueToken", "queue-token");

        // MSW가 받은 헤더를 보관하여 apiRequest가 요청을 어떻게 구성했는지 확인한다.
        let authorization: string | null = null;
        let queueToken: string | null = null;

        server.use(
            http.get(`${API_BASE_URL}/protected`, ({request}) => {
                authorization = request.headers.get("Authorization");
                queueToken = request.headers.get("Queue-Token");

                return HttpResponse.json({name: "테스트 사용자"});
            }),
        );

        await apiRequest("/protected");

        expect(authorization).toBe("Bearer access-token");
        expect(queueToken).toBe("queue-token");
    });

    it("실패 응답을 상태와 오류 코드를 가진 AppError로 변환한다", async () => {
        // 백엔드의 공통 오류 응답 구조와 HTTP 상태 코드를 함께 재현한다.
        server.use(
            http.get(`${API_BASE_URL}/admin`, () =>
                HttpResponse.json(
                    {
                        success: false,
                        errorCode: "ACCESS_DENIED",
                        message: "접근 권한이 없습니다.",
                        data: null,
                    },
                    {status: 403},
                ),
            ),
        );

        await expect(apiRequest("/admin")).rejects.toMatchObject({
            name: "AppError",
            status: 403,
            code: "ACCESS_DENIED",
            message: "접근 권한이 없습니다.",
        });
    });

    it("401 응답 후 토큰을 재발급하고 원 요청을 한 번 재시도한다", async () => {
        // 만료된 access token과 아직 유효한 refresh token이 저장된 상태를 재현한다.
        storage.local.set("accessToken", "expired-token");
        storage.local.set("refreshToken", "refresh-token");

        let protectedRequestCount = 0;
        let retryAuthorization: string | null = null;
        const authListener = vi.fn();
        unsubscribeAuth = subscribeAuth(authListener);

        server.use(
            http.get(`${API_BASE_URL}/protected`, ({request}) => {
                protectedRequestCount += 1;

                // 첫 요청은 만료된 토큰이므로 401을 반환한다.
                if (protectedRequestCount === 1) {
                    return new HttpResponse(null, {status: 401});
                }

                // 재시도 요청은 재발급된 토큰을 사용했는지 확인한 뒤 성공 응답을 반환한다.
                retryAuthorization = request.headers.get("Authorization");
                return HttpResponse.json({name: "테스트 사용자"});
            }),
            // apiRequest가 401을 받으면 호출할 토큰 재발급 API를 재현한다.
            http.post(`${API_BASE_URL}/auth/reissue`, () =>
                HttpResponse.json({
                    grantType: "Bearer",
                    accessToken: "new-access-token",
                    refreshToken: "new-refresh-token",
                }),
            ),
        );

        await expect(apiRequest("/protected")).resolves.toEqual({
            name: "테스트 사용자",
        });
        expect(protectedRequestCount).toBe(2);
        expect(retryAuthorization).toBe("Bearer new-access-token");
        expect(storage.local.get("accessToken")).toBe("new-access-token");
        expect(storage.local.get("refreshToken")).toBe("new-refresh-token");
        expect(authListener).toHaveBeenCalledTimes(1);
    });

    it("토큰 재발급이 실패하면 인증값을 모두 제거하고 만료를 알린다", async () => {
        storage.local.set("accessToken", "expired-token");
        storage.local.set("refreshToken", "invalid-refresh-token");
        storage.local.set("queueToken", "queue-token");
        storage.local.set("isVerified", "true");
        const authListener = vi.fn();
        unsubscribeAuth = subscribeAuth(authListener);

        server.use(
            http.get(`${API_BASE_URL}/protected`, () =>
                new HttpResponse(null, {status: 401}),
            ),
            http.post(`${API_BASE_URL}/auth/reissue`, () =>
                new HttpResponse(null, {status: 401}),
            ),
        );

        await expect(apiRequest("/protected")).rejects.toMatchObject({
            status: 401,
        });
        expect(storage.local.get("accessToken")).toBeNull();
        expect(storage.local.get("refreshToken")).toBeNull();
        expect(storage.local.get("queueToken")).toBeNull();
        expect(storage.local.get("isVerified")).toBeNull();
        expect(parseAuthSnapshot(getAuthSnapshot()).notice).toBe(
            "로그인이 만료되었습니다.",
        );
        expect(authListener).toHaveBeenCalledTimes(1);
    });

    it("refresh token이 없으면 남은 인증값을 제거한다", async () => {
        storage.local.set("accessToken", "invalid-access-token");
        storage.local.set("queueToken", "queue-token");
        storage.local.set("isVerified", "true");
        const authListener = vi.fn();
        unsubscribeAuth = subscribeAuth(authListener);

        server.use(
            http.get(`${API_BASE_URL}/protected`, () =>
                new HttpResponse(null, {status: 401}),
            ),
        );

        await expect(apiRequest("/protected")).rejects.toMatchObject({
            status: 401,
        });
        expect(storage.local.get("accessToken")).toBeNull();
        expect(storage.local.get("queueToken")).toBeNull();
        expect(storage.local.get("isVerified")).toBeNull();
        expect(authListener).toHaveBeenCalledTimes(1);
    });

    it("재발급한 토큰도 거부되면 세션을 만료한다", async () => {
        storage.local.set("accessToken", "expired-token");
        storage.local.set("refreshToken", "refresh-token");
        storage.local.set("isVerified", "true");
        const authListener = vi.fn();
        unsubscribeAuth = subscribeAuth(authListener);

        server.use(
            http.get(`${API_BASE_URL}/protected`, () =>
                new HttpResponse(null, {status: 401}),
            ),
            http.post(`${API_BASE_URL}/auth/reissue`, () =>
                HttpResponse.json({
                    grantType: "Bearer",
                    accessToken: "rejected-access-token",
                    refreshToken: "new-refresh-token",
                }),
            ),
        );

        await expect(apiRequest("/protected")).rejects.toMatchObject({
            status: 401,
        });
        expect(storage.local.get("accessToken")).toBeNull();
        expect(storage.local.get("refreshToken")).toBeNull();
        expect(storage.local.get("isVerified")).toBeNull();
        expect(authListener).toHaveBeenCalled();
    });

    it("동시에 받은 401 응답은 하나의 토큰 재발급 요청을 공유한다", async () => {
        // 서로 다른 API 두 개가 같은 만료 토큰으로 동시에 요청되는 상황을 준비한다.
        storage.local.set("accessToken", "expired-token");
        storage.local.set("refreshToken", "refresh-token");

        let refreshRequestCount = 0;

        server.use(
            http.get(`${API_BASE_URL}/protected/:id`, ({request}) => {
                const authorization = request.headers.get("Authorization");

                if (authorization === "Bearer expired-token") {
                    return new HttpResponse(null, {status: 401});
                }

                return HttpResponse.json({success: true});
            }),
            http.post(`${API_BASE_URL}/auth/reissue`, async () => {
                refreshRequestCount += 1;

                // 두 401 처리 시점이 실제로 겹치게 하여 공유 Promise가 없으면
                // 재발급 요청이 두 번 발생하도록 짧은 응답 지연을 둔다.
                await delay(20);

                return HttpResponse.json({
                    grantType: "Bearer",
                    accessToken: "new-access-token",
                    refreshToken: "new-refresh-token",
                });
            }),
        );

        // 두 요청을 순차적으로 기다리지 않고 같은 시점에 시작한다.
        await Promise.all([
            apiRequest("/protected/1"),
            apiRequest("/protected/2"),
        ]);

        expect(refreshRequestCount).toBe(1);
    });
});

describe("unwrap", () => {
    it("응답 데이터가 없으면 공통 AppError를 던진다", () => {
        // HTTP 요청은 성공했더라도 필수 data가 없으면 정상 결과로 사용하지 않는다.
        expect(() =>
            unwrap({
                success: true,
                errorCode: null,
                message: "성공",
                data: null,
            }),
        ).toThrow(
            new AppError(
                "서버 응답에 필요한 데이터가 없습니다.",
                500,
                "EMPTY_RESPONSE_DATA",
            ),
        );
    });
});
