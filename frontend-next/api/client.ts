import type {ApiResponse} from "@/types/api";
import type {TokenResponse} from "@/types/auth";

import {expireAuth, setAuthTokens} from "@/api/auth";
import {storage} from "@/lib/storage";

export const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export class AppError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly code?: string,
    ) {
        super(message);
        this.name = "AppError";
    }
}

async function parsePayload(response: Response) {
    const text = await response.text();
    if (!text) return null;

    try {
        return JSON.parse(text) as unknown;
    } catch {
        return null;
    }
}

let refreshPromise: Promise<boolean> | null = null;

/**
 * 저장된 refresh token으로 access token을 한 번 갱신한다.
 *
 * 여러 API 요청이 동시에 401을 받을 수 있으므로 최초 요청만 재발급 HTTP 요청을
 * 만들고, 나머지는 같은 Promise를 기다린다. 완료 후에는 다음 401에서 새 요청을
 * 만들 수 있도록 공유 Promise를 반드시 비운다.
 */
export async function refreshAccessToken() {
    const refreshToken = storage.local.get("refreshToken");
    if (!refreshToken) {
        expireAuth();
        return false;
    }

    if (!refreshPromise) {
        refreshPromise = fetch(`${API_BASE_URL}/auth/reissue`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({refreshToken}),
        })
            .then(async (response) => {
                if (!response.ok) return false;

                const tokens = (await parsePayload(response)) as TokenResponse | null;
                if (!tokens?.accessToken || !tokens.refreshToken) return false;

                setAuthTokens(tokens.accessToken, tokens.refreshToken);
                return true;
            })
            .catch(() => false)
            .then((refreshed) => {
                // 공유 Promise 안에서 한 번만 만료 처리해 동시 401마다 알림이 반복되지 않게 한다.
                if (!refreshed) expireAuth();
                return refreshed;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }

    return refreshPromise;
}

function createHeaders(options: RequestInit) {
    const headers = new Headers(options.headers);
    const accessToken = storage.local.get("accessToken");
    const queueToken = storage.local.get("queueToken");

    if (!headers.has("Content-Type") && options.body) {
        headers.set("Content-Type", "application/json");
    }
    if (accessToken) {
        headers.set("Authorization", `Bearer ${accessToken}`);
    }
    if (queueToken) {
        headers.set("Queue-Token", queueToken);
    }

    return headers;
}

export async function apiRequest<T>(
    path: string,
    options: RequestInit = {},
    allowRefresh = true,
): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: createHeaders(options),
    });

    if (response.status === 401 && allowRefresh && path !== "/auth/reissue") {
        // 1. 모든 동시 401 요청은 refreshAccessToken의 공유 Promise를 기다린다.
        const refreshed = await refreshAccessToken();

        if (refreshed) {
            // 2. 재발급된 토큰으로 원 요청을 정확히 한 번만 다시 보낸다.
            // 3. allowRefresh=false로 넘겨 재시도 응답이 다시 401이어도 반복하지 않는다.
            return apiRequest<T>(path, options, false);
        }
    }

    if (response.status === 401 && !allowRefresh) {
        // 재발급 직후의 토큰도 거부되면 더 복구하지 않고 현재 세션을 종료한다.
        expireAuth();
    }

    const payload = (await parsePayload(response)) as {
        message?: string;
        errorCode?: string;
    } | null;

    if (!response.ok) {
        throw new AppError(
            payload?.message ?? `요청에 실패했습니다. (${response.status})`,
            response.status,
            payload?.errorCode,
        );
    }

    return payload as T;
}

export function unwrap<T>(response: ApiResponse<T>): T {
    if (response.data === null || response.data === undefined) {
        throw new AppError(
            "서버 응답에 필요한 데이터가 없습니다.",
            500,
            "EMPTY_RESPONSE_DATA",
        );
    }

    return response.data;
}
