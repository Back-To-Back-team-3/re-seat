import type { UserProfile, UserRole } from "@/types/auth";

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
      <button
        className="cursor-pointer rounded-control border-0 bg-[#fee500] px-[17px] py-[11px] text-[13px] font-extrabold text-[#191919]"
        onClick={onLogin}
        type="button"
      >
        카카오 로그인
      </button>
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
      <button
        className="cursor-pointer border-0 bg-transparent p-1.5 text-[11px] text-muted-foreground max-sm:hidden"
        onClick={onLogout}
        type="button"
      >
        로그아웃
      </button>
    </div>
  );
}
