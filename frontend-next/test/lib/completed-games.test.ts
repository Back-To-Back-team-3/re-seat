import { beforeEach, describe, expect, it } from "vitest";

import {
  getCompletedGameIds,
  rememberCompletedGame,
} from "@/lib/completed-games";

describe("완료 경기 저장소", () => {
  beforeEach(() => localStorage.clear());

  it("결제가 끝난 경기 ID를 중복 없이 누적한다", () => {
    rememberCompletedGame(10);
    rememberCompletedGame(20);
    rememberCompletedGame(10);

    expect([...getCompletedGameIds()]).toEqual([10, 20]);
  });
});
