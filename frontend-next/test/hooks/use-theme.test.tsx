import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it} from "vitest";

import {useTheme} from "@/hooks/use-theme";

function ThemeProbe() {
    const {theme, toggleTheme} = useTheme();
    return (
        <button onClick={toggleTheme} type="button">
            {theme}
        </button>
    );
}

describe("useTheme", () => {
    beforeEach(() => {
        localStorage.clear();
        document.documentElement.removeAttribute("data-theme");
    });

    afterEach(() => {
        cleanup();
    });

    it("마운트 시 저장된 테마를 document root에 반영한다", () => {
        localStorage.setItem("theme", "dark");

        render(<ThemeProbe/>);

        expect(document.documentElement.dataset.theme).toBe("dark");
        expect(screen.getByRole("button")).toHaveTextContent("dark");
    });

    it("토글하면 테마, 저장값, document root 속성이 함께 바뀐다", () => {
        render(<ThemeProbe/>);

        fireEvent.click(screen.getByRole("button"));

        expect(document.documentElement.dataset.theme).toBe("dark");
        expect(localStorage.getItem("theme")).toBe("dark");

        fireEvent.click(screen.getByRole("button"));

        expect(document.documentElement.dataset.theme).toBe("light");
        expect(localStorage.getItem("theme")).toBe("light");
    });

    it("다른 탭에서 storage에 기록된 테마를 리마운트 없이 반영한다", () => {
        render(<ThemeProbe/>);

        localStorage.setItem("theme", "dark");
        fireEvent(window, new Event("storage"));

        expect(screen.getByRole("button")).toHaveTextContent("dark");
        expect(document.documentElement.dataset.theme).toBe("dark");
    });
});
