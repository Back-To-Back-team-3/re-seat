import Link from "next/link";
import {ChevronDown, LayoutDashboard, LogOut, UserRound} from "lucide-react";

import {Button} from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLinkItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type {UserProfile, UserRole} from "@/types/auth";

type LoginPanelProps = {
    isAuthed: boolean;
    profile: UserProfile | null;
    role: UserRole | null;
    onLogin: () => void;
    onLogout: () => void;
};

/**
 * 상단 바에서 로그인 전·후 사용자 동작을 같은 자리에서 전환한다.
 *
 * 인증 상태의 소유권은 useAuth에 두고 이 컴포넌트는 표시와 클릭 전달만 담당한다.
 */
export function LoginPanel({
                               isAuthed,
                               profile,
                               role,
                               onLogin,
                               onLogout,
                           }: LoginPanelProps) {
    if (!isAuthed) {
        return (
            <Button
                className="h-10 border-0 bg-[#fee500] px-[17px] text-[13px] text-[#191919] shadow-none hover:bg-[#f2d900]"
                onClick={onLogin}
                size="sm"
                type="button"
            >
                카카오 로그인
            </Button>
        );
    }

    const displayName = profile?.nickname || profile?.name || "회원";
    const initial = (
        profile?.nickname ||
        profile?.name ||
        profile?.email ||
        "U"
    ).slice(0, 1);

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                aria-label="프로필 메뉴"
                className="flex h-11 items-center gap-2 rounded-control px-1.5 text-left outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/35"
            >
                <span className="grid size-9 place-items-center rounded-full bg-foreground font-extrabold text-surface">
                    {initial}
                </span>
                <span className="grid gap-px max-sm:hidden">
                    <strong className="max-w-[100px] overflow-hidden text-xs text-ellipsis whitespace-nowrap">
                        {displayName}
                    </strong>
                    <small className="text-[9px] tracking-[1px] text-muted-foreground">
                        {role}
                    </small>
                </span>
                <ChevronDown aria-hidden="true" className="text-muted-foreground max-sm:hidden"/>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
                <DropdownMenuLinkItem render={<Link href="/mypage"/>}>
                    <UserRound aria-hidden="true"/>
                    마이페이지
                </DropdownMenuLinkItem>
                {role === "ADMIN" && (
                    <DropdownMenuLinkItem render={<Link href="/admin"/>}>
                        <LayoutDashboard aria-hidden="true"/>
                        관리자 페이지
                    </DropdownMenuLinkItem>
                )}
                <DropdownMenuSeparator/>
                <DropdownMenuItem className="text-destructive" onClick={onLogout}>
                    <LogOut aria-hidden="true"/>
                    로그아웃
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
