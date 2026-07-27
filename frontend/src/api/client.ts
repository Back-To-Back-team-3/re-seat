import type { ApiResponse, ApiResult, TokenResponse } from "../types";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
export const BACKEND_BASE_URL = import.meta.env.VITE_BACKEND_BASE_URL
  ?? API_BASE_URL.replace(/\/api\/v1\/?$/, "");
const USE_MOCK_FALLBACK = (import.meta.env.VITE_USE_MOCK_FALLBACK ?? "true") === "true";

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getAccessToken() {
  return localStorage.getItem("accessToken");
}

export function setTokens(accessToken: string, refreshToken?: string) {
  localStorage.setItem("accessToken", accessToken);
  if (refreshToken) {
    localStorage.setItem("refreshToken", refreshToken);
  }
}

export function clearTokens() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("queueToken");
}

export function setQueueToken(token: string | null) {
  if (token) {
    localStorage.setItem("queueToken", token);
  } else {
    localStorage.removeItem("queueToken");
  }
}

export function getQueueToken() {
  return localStorage.getItem("queueToken");
}

export function getAccessTokenRole(): "USER" | "ADMIN" {
  const token = getAccessToken();
  if (!token) return "USER";

  try {
    const payload = token.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const claims = JSON.parse(atob(padded)) as { userRole?: string };
    return claims.userRole === "ADMIN" ? "ADMIN" : "USER";
  } catch {
    return "USER";
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

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem("refreshToken");
  if (!refreshToken) return false;

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/auth/reissue`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken })
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const tokens = await parsePayload(response) as TokenResponse | null;
        if (!tokens?.accessToken || !tokens.refreshToken) return false;
        setTokens(tokens.accessToken, tokens.refreshToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }

  const refreshed = await refreshPromise;
  if (!refreshed) clearTokens();
  return refreshed;
}

function createHeaders(options: RequestInit) {
  const headers = new Headers(options.headers);
  const token = getAccessToken();
  const queueToken = getQueueToken();

  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (queueToken) headers.set("Queue-Token", queueToken);
  return headers;
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  allowRefresh = true
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: createHeaders(options)
  });

  if (response.status === 401 && allowRefresh && path !== "/auth/reissue") {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiRequest<T>(path, options, false);
  }

  const payload = await parsePayload(response) as {
    message?: string;
    errorCode?: string;
  } | null;

  if (!response.ok) {
    throw new ApiError(
      payload?.message ?? `요청에 실패했습니다. (${response.status})`,
      response.status,
      payload?.errorCode
    );
  }

  return payload as T;
}

async function openSse(path: string, signal: AbortSignal, allowRefresh: boolean): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers({ Accept: "text/event-stream" });
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { headers, signal });
  if (response.status === 401 && allowRefresh && await refreshAccessToken()) {
    return openSse(path, signal, false);
  }
  return response;
}

export async function streamSse(
  path: string,
  onEvent: (event: string, data: unknown) => void,
  signal: AbortSignal
) {
  const response = await openSse(path, signal, true);
  if (!response.ok || !response.body) {
    throw new ApiError("대기열 실시간 연결에 실패했습니다.", response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? "";

    blocks.forEach((block) => {
      let eventName = "message";
      const dataLines: string[] = [];

      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
      });

      if (dataLines.length > 0) {
        onEvent(eventName, JSON.parse(dataLines.join("\n")));
      }
    });
  }
}

export function unwrap<T>(response: ApiResponse<T>): T {
  if (response.data === null || response.data === undefined) {
    throw new ApiError("서버 응답에 필요한 데이터가 없습니다.", 500, "EMPTY_RESPONSE_DATA");
  }
  return response.data;
}

export async function withMockFallback<T>(
  apiCall: () => Promise<T>,
  mockValue: T,
  mockMessage = "현재 백엔드에 조회 API가 없어 샘플 데이터로 표시 중입니다."
): Promise<ApiResult<T>> {
  try {
    return { data: await apiCall(), source: "api" };
  } catch (error) {
    if (!USE_MOCK_FALLBACK) throw error;
    return { data: mockValue, source: "mock", message: mockMessage };
  }
}
