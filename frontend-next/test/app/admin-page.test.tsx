import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import AdminPage from "@/app/admin/page";

const mocks = vi.hoisted(() => ({
    auth: {
        busy: false,
        isAuthed: true,
        role: "ADMIN" as "ADMIN" | "USER",
    },
}));

vi.mock("@/hooks/use-auth", () => ({
    useAuth: () => mocks.auth,
}));
vi.mock("@/components/admin/users/admin-user-management", () => ({
    AdminUserManagement: () => <div>회원 관리 화면</div>,
}));
vi.mock("@/components/admin/games/admin-game-management", () => ({
    AdminGameManagement: () => <div>경기 관리 화면</div>,
}));

describe("관리자 페이지", () => {
    beforeEach(() => {
        Object.assign(mocks.auth, {
            busy: false,
            isAuthed: true,
            role: "ADMIN",
        });
    });

    afterEach(cleanup);

    it("회원 관리 화면을 기본 탭으로 표시하고 경기 관리로 전환한다", () => {
        render(<AdminPage/>);

        expect(screen.getByText("회원 관리 화면")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: "경기/좌석 관리"}));

        expect(screen.getByText("경기 관리 화면")).toBeInTheDocument();
    });

    it("일반 사용자의 접근을 차단한다", () => {
        mocks.auth.role = "USER";

        render(<AdminPage/>);

        expect(
            screen.getByRole("heading", {name: "접근 권한이 없습니다"}),
        ).toBeInTheDocument();
        expect(screen.queryByText("회원 관리 화면")).not.toBeInTheDocument();
    });
});
