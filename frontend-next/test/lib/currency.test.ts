import {describe, expect, it} from "vitest";

import {calculateTotalPrice, formatPrice} from "@/lib/currency";

describe("calculateTotalPrice", () => {
    it("가격을 가진 항목의 합계를 계산한다", () => {
        expect(calculateTotalPrice([{price: 12_000}, {price: 18_000}])).toBe(
            30_000,
        );
    });
});

describe("formatPrice", () => {
    it("금액에 한국어 천 단위 구분과 원 단위를 표시한다", () => {
        expect(formatPrice(18_000)).toBe("18,000원");
    });
});
