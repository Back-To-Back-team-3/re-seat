import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

import {MyPageNavigation} from "@/components/mypage/mypage-navigation";

describe("MyPageNavigation", () => {
    it("선택한 관리 화면을 전달하고 현재 탭을 표시한다", () => {
        const onSelect = vi.fn();

        render(
            <MyPageNavigation activeTab="tickets" onSelect={onSelect}/>,
        );

        expect(screen.getByRole("tab", {name: "티켓 관리"})).toHaveAttribute(
            "aria-selected",
            "true",
        );

        fireEvent.click(screen.getByRole("tab", {name: "회원정보 관리"}));

        expect(onSelect).toHaveBeenCalledWith("account");
    });
});
