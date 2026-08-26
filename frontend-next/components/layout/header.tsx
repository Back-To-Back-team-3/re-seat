"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";

import {LoginPanel} from "@/components/auth/login-panel";
import {useAuth} from "@/hooks/use-auth";
import {useTheme} from "@/hooks/use-theme";

const ACTIVE_NAV_LINK =
    "relative grid place-items-center px-1 text-sm font-bold text-foreground after:absolute after:right-0 after:bottom-0 after:left-0 after:h-0.5 after:bg-brand after:content-['']";
const INACTIVE_NAV_LINK =
    "grid place-items-center px-1 text-sm font-bold text-muted-foreground hover:text-foreground";
const DISABLED_NAV_LINK =
    "pointer-events-none grid place-items-center px-1 text-sm font-bold text-muted-foreground opacity-50";

/**
 * 모든 공개·예매 route가 공유하는 상단 셸이다.
 *
 * 인증 상태는 useAuth, 테마는 useTheme이 각각 소유하며 이 컴포넌트는 두 상태를
 * 화면에 배치하는 역할만 한다. 현재 route는 usePathname으로만 계산해 별도
 * 전역 nav 상태를 만들지 않는다.
 */
export function Header() {
    const auth = useAuth();
    const pathname = usePathname();
    const {theme, toggleTheme} = useTheme();
    const isTicketsRoute = pathname.startsWith("/tickets");

    return (
        <header
            className="sticky top-0 z-50 grid min-h-[70px] grid-cols-[auto_1fr_auto] items-center border-b border-border bg-background/92 px-[var(--gutter-desktop)] shadow-[0_1px_12px_rgb(0_0_0/4%)] backdrop-blur-lg max-[900px]:px-6 max-sm:min-h-[62px] max-sm:px-[var(--gutter-mobile)]">
            <Link
                aria-label="Re:Seat 홈"
                className="font-brand text-[28px] font-black tracking-[-1.5px] text-foreground max-sm:text-2xl"
                href="/games"
            >
                Re:<span className="text-brand">Seat</span>
            </Link>
            <nav
                aria-label="주요 메뉴"
                className="flex h-[70px] justify-center gap-[30px] max-[900px]:hidden"
            >
                <Link
                    className={isTicketsRoute ? INACTIVE_NAV_LINK : ACTIVE_NAV_LINK}
                    href="/games"
                >
                    경기 예매
                </Link>
                <Link
                    aria-disabled={!auth.isAuthed}
                    className={
                        !auth.isAuthed
                            ? DISABLED_NAV_LINK
                            : isTicketsRoute
                                ? ACTIVE_NAV_LINK
                                : INACTIVE_NAV_LINK
                    }
                    href="/tickets"
                    onClick={
                        auth.isAuthed
                            ? undefined
                            : (event) => {
                                event.preventDefault();
                            }
                    }
                    tabIndex={auth.isAuthed ? undefined : -1}
                >
                    내 티켓
                </Link>
            </nav>
            <div className="flex items-center justify-end gap-2.5">
                <button
                    aria-label="화면 테마 변경"
                    className="grid size-[38px] place-items-center rounded-full border border-border bg-surface max-sm:hidden"
                    onClick={toggleTheme}
                    type="button"
                >
                    {theme === "dark" ? "☀" : "☾"}
                </button>
                <LoginPanel
                    isAuthed={auth.isAuthed}
                    onLogin={auth.login}
                    onLogout={auth.logout}
                    profile={auth.profile}
                    role={auth.role}
                />
            </div>
        </header>
    );
}
