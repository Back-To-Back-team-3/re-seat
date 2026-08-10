import {describe, expect, it} from "vitest";

import {formatGameDate, parseApiDateTime} from "@/lib/date";

describe("parseApiDateTime", () => {
    it("오프셋 없는 백엔드 일시를 KST로 해석한다", () => {
        expect(parseApiDateTime("2026-07-11 18:30:00")?.toISOString()).toBe(
            "2026-07-11T09:30:00.000Z",
        );
    });

    it("해석할 수 없는 값은 null을 반환한다", () => {
        expect(parseApiDateTime("경기 시간 미정")).toBeNull();
    });
});

describe("formatGameDate", () => {
    it("경기 일시를 기존 화면과 같은 한국어 형식으로 표시한다", () => {
        expect(formatGameDate("2026-07-11 18:30:00")).toBe(
            "7월 11일 (토) 18:30",
        );
    });

    it("해석할 수 없는 값은 원문을 유지한다", () => {
        expect(formatGameDate("경기 시간 미정")).toBe("경기 시간 미정");
    });
});
