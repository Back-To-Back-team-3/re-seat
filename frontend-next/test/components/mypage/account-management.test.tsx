import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {AccountManagement} from "@/components/mypage/account-management";

const PROFILE = {
    id: 1,
    email: "user@example.com",
    name: "테스트 사용자",
    nickname: "야구팬",
    phone: "010-1234-5678",
    isVerified: true,
};

afterEach(cleanup);

describe("AccountManagement", () => {
    it("수정한 이름과 전화번호를 저장 요청으로 전달한다", async () => {
        const onUpdate = vi.fn().mockResolvedValue(undefined);

        render(
            <AccountManagement
                isUpdating={false}
                isWithdrawing={false}
                onUpdate={onUpdate}
                onWithdraw={vi.fn()}
                profile={PROFILE}
            />,
        );

        fireEvent.change(screen.getByLabelText("이름"), {
            target: {value: "새 이름"},
        });
        fireEvent.change(screen.getByLabelText("전화번호"), {
            target: {value: "010-9876-5432"},
        });
        fireEvent.click(screen.getByRole("button", {name: "변경사항 저장"}));

        await waitFor(() => {
            expect(onUpdate).toHaveBeenCalledWith({
                name: "새 이름",
                phone: "010-9876-5432",
            });
        });
    });

    it("확인 다이얼로그에서 동의한 경우에만 회원 탈퇴를 요청한다", async () => {
        const onWithdraw = vi.fn();

        render(
            <AccountManagement
                isUpdating={false}
                isWithdrawing={false}
                onUpdate={vi.fn()}
                onWithdraw={onWithdraw}
                profile={PROFILE}
            />,
        );

        fireEvent.click(screen.getByRole("button", {name: "회원 탈퇴"}));
        expect(onWithdraw).not.toHaveBeenCalled();

        fireEvent.click(
            await screen.findByRole("button", {name: "탈퇴 확인"}),
        );

        expect(onWithdraw).toHaveBeenCalledOnce();
    });
});
