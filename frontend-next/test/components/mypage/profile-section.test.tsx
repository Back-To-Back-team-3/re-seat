import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

import {ProfileSection} from "@/components/mypage/profile-section";

const PROFILE = {
    id: 1,
    email: "user@example.com",
    name: "테스트 사용자",
    nickname: "야구팬",
    phone: "010-1234-5678",
    isVerified: true,
};

describe("ProfileSection", () => {
    it("회원 탈퇴를 확인하기 전에는 탈퇴 요청을 전달하지 않는다", async () => {
        const onWithdraw = vi.fn();

        render(
            <ProfileSection
                isVerified
                onLogout={vi.fn()}
                onWithdraw={onWithdraw}
                profile={PROFILE}
                role="USER"
            />,
        );

        fireEvent.click(screen.getByRole("button", {name: "회원 탈퇴"}));

        expect(
            await screen.findByRole("dialog", {name: "회원 탈퇴 안내"}),
        ).toBeInTheDocument();
        expect(onWithdraw).not.toHaveBeenCalled();
    });

    it("탈퇴 확인을 누르면 탈퇴 요청을 전달한다", async () => {
        const onWithdraw = vi.fn();

        render(
            <ProfileSection
                isVerified
                onLogout={vi.fn()}
                onWithdraw={onWithdraw}
                profile={PROFILE}
                role="USER"
            />,
        );

        fireEvent.click(screen.getByRole("button", {name: "회원 탈퇴"}));
        fireEvent.click(
            await screen.findByRole("button", {name: "탈퇴 확인"}),
        );

        expect(onWithdraw).toHaveBeenCalledOnce();
    });
});
