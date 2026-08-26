import {beforeEach, describe, expect, it} from "vitest";

import {getAccessTokenRole, getServerAuthSnapshot, parseAuthSnapshot,} from "@/api/auth";

function createAccessToken(userRole: "USER" | "ADMIN") {
    const payload = btoa(JSON.stringify({userRole}));
    return `header.${payload}.signature`;
}

describe("인증 역할 해석", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it("로그아웃 상태에는 사용자 역할이 없다", () => {
        expect(getAccessTokenRole()).toBeNull();
        expect(parseAuthSnapshot(getServerAuthSnapshot()).role).toBeNull();
    });

    it("손상된 access token은 사용자 역할로 간주하지 않는다", () => {
        localStorage.setItem("accessToken", "invalid-token");

        expect(getAccessTokenRole()).toBeNull();
    });

    it.each([
        ["일반 사용자", "USER"],
        ["관리자", "ADMIN"],
    ] as const)("정상 토큰에서 %s 역할을 읽는다", (_, userRole) => {
        localStorage.setItem("accessToken", createAccessToken(userRole));

        expect(getAccessTokenRole()).toBe(userRole);
    });
});
