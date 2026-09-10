"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {
    CalendarDays,
    Home,
    LayoutDashboard,
    Menu,
    Moon,
    Sun,
    UserRound,
} from "lucide-react";
import {useState} from "react";

import {LoginPanel} from "@/components/auth/login-panel";
import {Button} from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import {useAuth} from "@/hooks/use-auth";
import {useTheme} from "@/hooks/use-theme";

const ACTIVE_NAV_LINK =
    "relative grid place-items-center px-1 text-sm font-bold text-foreground after:absolute after:right-0 after:bottom-0 after:left-0 after:h-0.5 after:bg-brand after:content-['']";
const INACTIVE_NAV_LINK =
    "grid place-items-center px-1 text-sm font-bold text-muted-foreground hover:text-foreground";
const DISABLED_NAV_LINK =
    "pointer-events-none grid place-items-center px-1 text-sm font-bold text-muted-foreground opacity-50";

type NavLinkProps = {
    href: string;
    label: string;
    active: boolean;
    disabled?: boolean;
};

function NavLink({href, label, active, disabled = false}: NavLinkProps) {
    return (
        <Link
            aria-disabled={disabled || undefined}
            className={
                disabled
                    ? DISABLED_NAV_LINK
                    : active
                        ? ACTIVE_NAV_LINK
                        : INACTIVE_NAV_LINK
            }
            href={href}
            onClick={
                disabled
                    ? (event) => {
                        event.preventDefault();
                    }
                    : undefined
            }
            tabIndex={disabled ? -1 : undefined}
        >
            {label}
        </Link>
    );
}

/**
 * 모든 공개·예매 route가 공유하는 상단 셸이다.
 *
 * 인증 상태는 useAuth, 테마는 useTheme이 각각 소유하며 이 컴포넌트는 두 상태를
 * 화면에 배치하는 역할만 한다. 현재 route는 usePathname으로만 계산해 별도
 * 전역 nav 상태를 만들지 않는다.
 */
export function Header() {
    const auth = useAuth();
    const pathname = usePathname() ?? "";
    const {theme, toggleTheme} = useTheme();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const isHomeRoute = pathname === "/";
    const isBookingRoute = pathname.startsWith("/games");
    const isMyPageRoute = pathname.startsWith("/mypage");

    return (
        <header
            className="sticky top-0 z-50 grid min-h-[70px] grid-cols-[auto_1fr_auto] items-center border-b border-border bg-background/92 px-[var(--gutter-desktop)] shadow-[0_1px_12px_rgb(0_0_0/4%)] backdrop-blur-lg max-[900px]:px-6 max-sm:min-h-[62px] max-sm:px-[var(--gutter-mobile)]">
            <Link
                aria-label="Re:Seat 홈"
                className="font-brand text-[28px] font-black tracking-[-1.5px] text-foreground max-sm:text-2xl"
                href="/"
            >
                Re:<span className="text-brand">Seat</span>
            </Link>
            <nav
                aria-label="주요 메뉴"
                className="flex h-[70px] justify-center gap-[30px] max-[900px]:hidden"
            >
                <NavLink active={isHomeRoute} href="/" label="홈"/>
                <NavLink active={isBookingRoute} href="/games" label="예매"/>
                <NavLink
                    active={isMyPageRoute}
                    disabled={!auth.isAuthed}
                    href="/mypage"
                    label="마이페이지"
                />
            </nav>
            <div className="flex items-center justify-end gap-2.5">
                <Button
                    aria-label="화면 테마 변경"
                    className="size-[38px] rounded-full max-[900px]:hidden"
                    onClick={toggleTheme}
                    size="icon-sm"
                    type="button"
                    variant="outline"
                >
                    {theme === "dark" ? (
                        <Sun aria-hidden="true"/>
                    ) : (
                        <Moon aria-hidden="true"/>
                    )}
                </Button>
                <div className="max-[900px]:hidden">
                    <LoginPanel
                        isAuthed={auth.isAuthed}
                        onLogin={auth.login}
                        onLogout={auth.logout}
                        profile={auth.profile}
                        role={auth.role}
                    />
                </div>
                <Button
                    aria-label="모바일 화면 테마 변경"
                    className="hidden rounded-full max-[900px]:inline-flex"
                    onClick={toggleTheme}
                    size="icon-sm"
                    type="button"
                    variant="ghost"
                >
                    {theme === "dark" ? (
                        <Sun aria-hidden="true"/>
                    ) : (
                        <Moon aria-hidden="true"/>
                    )}
                </Button>
                <Dialog open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
                    <DialogTrigger
                        render={
                            <Button
                                aria-label="전체 메뉴 열기"
                                className="hidden max-[900px]:inline-flex"
                                size="icon-sm"
                                type="button"
                                variant="ghost"
                            />
                        }
                    >
                        <Menu aria-hidden="true"/>
                    </DialogTrigger>
                    <DialogContent
                        className="top-0 right-0 bottom-0 left-auto h-dvh w-[min(86vw,340px)] max-w-none translate-x-0 translate-y-0 content-start rounded-none border-y-0 border-r-0 p-0"
                    >
                        <div className="border-b border-border px-6 py-5">
                            <DialogTitle>메뉴</DialogTitle>
                        </div>
                        <nav
                            aria-label="모바일 주요 메뉴"
                            className="grid gap-1 px-4 py-4"
                        >
                            <Link
                                className="flex h-12 items-center gap-3 rounded-control px-3 font-bold hover:bg-muted"
                                href="/"
                                onClick={() => setMobileMenuOpen(false)}
                            >
                                <Home aria-hidden="true"/> 홈
                            </Link>
                            <Link
                                className="flex h-12 items-center gap-3 rounded-control px-3 font-bold hover:bg-muted"
                                href="/games"
                                onClick={() => setMobileMenuOpen(false)}
                            >
                                <CalendarDays aria-hidden="true"/> 예매
                            </Link>
                            {auth.isAuthed && (
                                <Link
                                    className="flex h-12 items-center gap-3 rounded-control px-3 font-bold hover:bg-muted"
                                    href="/mypage"
                                    onClick={() => setMobileMenuOpen(false)}
                                >
                                    <UserRound aria-hidden="true"/> 마이페이지
                                </Link>
                            )}
                            {auth.role === "ADMIN" && (
                                <Link
                                    className="flex h-12 items-center gap-3 rounded-control px-3 font-bold hover:bg-muted"
                                    href="/admin"
                                    onClick={() => setMobileMenuOpen(false)}
                                >
                                    <LayoutDashboard aria-hidden="true"/> 관리자 페이지
                                </Link>
                            )}
                        </nav>
                        <div className="mt-auto grid gap-2 border-t border-border p-4">
                            {auth.isAuthed ? (
                                <Button onClick={auth.logout} type="button" variant="destructive">
                                    로그아웃
                                </Button>
                            ) : (
                                <Button onClick={auth.login} type="button">
                                    카카오 로그인
                                </Button>
                            )}
                        </div>
                    </DialogContent>
                </Dialog>
            </div>
        </header>
    );
}
