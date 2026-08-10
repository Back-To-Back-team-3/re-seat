import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {storage} from "@/lib/storage";

describe("storage", () => {
    beforeEach(() => {
        window.localStorage.clear();
        window.sessionStorage.clear();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("localStorage 값을 저장하고 읽는다", () => {
        storage.local.set("accessToken", "access-token");

        expect(storage.local.get("accessToken")).toBe("access-token");
    });

    it("sessionStorage의 JSON 값을 파싱한다", () => {
        window.sessionStorage.setItem("pendingPayment", '{"orderId":17}');

        expect(storage.session.getJson("pendingPayment")).toEqual({orderId: 17});
    });

    it("저장된 JSON이 손상되었으면 null을 반환한다", () => {
        window.sessionStorage.setItem("pendingPayment", "{broken");

        expect(storage.session.getJson("pendingPayment")).toBeNull();
    });

    it("서버 환경에서는 브라우저 저장소를 읽지 않는다", () => {
        vi.stubGlobal("window", undefined);

        expect(storage.local.get("accessToken")).toBeNull();
        expect(storage.session.getJson("pendingPayment")).toBeNull();
    });
});
