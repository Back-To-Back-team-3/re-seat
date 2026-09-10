"use client";

import Link from "next/link";
import {LayoutDashboard, ShieldAlert} from "lucide-react";

import {buttonVariants} from "@/components/ui/button";
import {useAuth} from "@/hooks/use-auth";

/** 관리자 기능이 배치될 보호된 진입 화면이다. */
export default function AdminPage() {
    const auth = useAuth();

    if (auth.busy) {
        return (
            <main className="mx-auto min-h-[60vh] max-w-6xl px-[var(--gutter-desktop)] py-16">
                관리자 권한을 확인하고 있습니다.
            </main>
        );
    }

    if (!auth.isAuthed || auth.role !== "ADMIN") {
        return (
            <main className="mx-auto grid min-h-[60vh] max-w-6xl place-items-center px-[var(--gutter-desktop)] py-16 text-center">
                <div className="grid max-w-md justify-items-center gap-4">
                    <ShieldAlert aria-hidden="true" className="size-10 text-destructive"/>
                    <h1 className="text-2xl font-black">접근 권한이 없습니다</h1>
                    <p className="text-sm leading-6 text-muted-foreground">
                        관리자 계정으로 로그인한 경우에만 이 페이지를 이용할 수
                        있습니다.
                    </p>
                    <Link className={buttonVariants()} href="/">
                        홈으로 이동
                    </Link>
                </div>
            </main>
        );
    }

    return (
        <main className="mx-auto min-h-[60vh] max-w-6xl px-[var(--gutter-desktop)] py-16 max-sm:px-[var(--gutter-mobile)]">
            <div className="flex items-center gap-3">
                <LayoutDashboard aria-hidden="true" className="size-8 text-brand"/>
                <div>
                    <p className="text-xs font-bold text-brand">ADMIN</p>
                    <h1 className="text-3xl font-black">관리자 페이지</h1>
                </div>
            </div>
            <p className="mt-6 text-muted-foreground">
                관리 기능은 다음 화면 개편 단계에서 연결됩니다.
            </p>
        </main>
    );
}
