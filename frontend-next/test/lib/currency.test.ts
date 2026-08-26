import {describe, expect, it} from "vitest";

import {formatPrice} from "@/lib/currency";

describe("formatPrice", () => {
    it("금액에 한국어 천 단위 구분과 원 단위를 표시한다", () => {
        expect(formatPrice(18_000)).toBe("18,000원");
    });
});
