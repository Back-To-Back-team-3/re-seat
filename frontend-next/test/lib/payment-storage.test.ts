import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  beginPaymentCallback,
  completePaymentCallback,
  getPaymentKey,
} from "@/lib/payment-storage";

describe("결제 세션 저장소", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubGlobal("crypto", { randomUUID: () => "fixed-key" });
  });

  it("같은 주문의 멱등키를 다시 생성하지 않고 재사용한다", () => {
    expect(getPaymentKey(10)).toBe("fixed-key");
    expect(getPaymentKey(10)).toBe("fixed-key");
  });

  it("처리 중이거나 완료된 결제 callback의 중복 실행을 막는다", () => {
    expect(beginPaymentCallback(20)).toBe(true);
    expect(beginPaymentCallback(20)).toBe(false);
    completePaymentCallback(20);
    expect(beginPaymentCallback(20)).toBe(false);
  });
});
