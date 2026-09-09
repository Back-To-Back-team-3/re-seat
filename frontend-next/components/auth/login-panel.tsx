import {LogOut} from "lucide-react";

import {Button} from "@/components/ui/button";
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
        <div className="flex items-center gap-[9px]">
      <span className="grid size-9 place-items-center rounded-full bg-foreground font-extrabold text-surface">
        {initial}
      </span>
            <div className="grid gap-px max-sm:hidden">
                <strong className="max-w-[100px] overflow-hidden text-xs text-ellipsis whitespace-nowrap">
                    {displayName}
                </strong>
                <small className="text-[9px] tracking-[1px] text-muted-foreground">
                    {role}
                </small>
            </div>
            <Button
                className="h-8 px-2 text-[11px] text-muted-foreground max-sm:hidden"
                onClick={onLogout}
                size="sm"
                type="button"
                variant="ghost"
            >
                <LogOut aria-hidden="true"/>
                로그아웃
            </Button>
        </div>
    );
}
