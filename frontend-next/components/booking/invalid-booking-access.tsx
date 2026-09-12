import Link from "next/link";
import {ShieldAlert} from "lucide-react";

import {buttonVariants} from "@/components/ui/button";

/** 정상적인 예매 진입 조건을 충족하지 못한 사용자에게 복귀 경로를 안내한다. */
export function InvalidBookingAccess() {
    return (
        <section className="grid min-h-[520px] place-items-center px-6 py-16 text-center">
            <div className="grid max-w-md justify-items-center gap-4">
                <ShieldAlert
                    aria-hidden="true"
                    className="size-11 text-destructive"
                />
                <h1 className="text-2xl font-black">비정상적인 접근입니다</h1>
                <p className="text-sm leading-6 text-muted-foreground">
                    예매를 시작하려면 로그인한 뒤 홈이나 경기 일정에서 예매 가능한
                    경기를 선택해 주세요.
                </p>
                <Link className={buttonVariants()} href="/">
                    홈으로 이동
                </Link>
            </div>
        </section>
    );
}
