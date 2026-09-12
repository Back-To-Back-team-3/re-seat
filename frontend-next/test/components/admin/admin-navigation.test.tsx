import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

import {AdminNavigation} from "@/components/admin/admin-navigation";

describe("AdminNavigation", () => {
    it("현재 메뉴를 표시하고 선택한 관리자 메뉴를 전달한다", () => {
        const onSelect = vi.fn();

        render(<AdminNavigation activeTab="users" onSelect={onSelect}/>);

        expect(screen.getByRole("tab", {name: "회원 관리"})).toHaveAttribute(
            "aria-selected",
            "true",
        );
        fireEvent.click(screen.getByRole("tab", {name: "경기/좌석 관리"}));

        expect(onSelect).toHaveBeenCalledWith("games");
    });
});
