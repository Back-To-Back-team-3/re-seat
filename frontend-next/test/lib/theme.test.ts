import {beforeEach, describe, expect, it} from "vitest";

import {getStoredTheme, setStoredTheme} from "@/lib/theme";

describe("theme storage", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it("저장된 값이 없으면 light를 기본값으로 반환한다", () => {
        expect(getStoredTheme()).toBe("light");
    });

    it("저장된 값이 dark면 dark를 반환한다", () => {
        localStorage.setItem("theme", "dark");
        expect(getStoredTheme()).toBe("dark");
    });

    it("알 수 없는 값이 저장되어 있으면 light로 취급한다", () => {
        localStorage.setItem("theme", "purple");
        expect(getStoredTheme()).toBe("light");
    });

    it("setStoredTheme은 localStorage에 값을 기록한다", () => {
        setStoredTheme("dark");
        expect(localStorage.getItem("theme")).toBe("dark");
    });
});
