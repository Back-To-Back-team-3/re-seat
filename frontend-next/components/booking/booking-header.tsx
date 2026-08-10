"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";

import {BookingProgress, type BookingStep,} from "@/components/booking/booking-progress";

const STEP_LABELS: Record<BookingStep, string> = {
    queue: "예매 대기",
    seats: "좌석 선택",
    checkout: "주문",
    payment: "결제",
};

/**
 * 예매 영역의 현재 위치와 전체 진행 단계를 함께 보여준다.
 *
 * 실제 URL은 기존 사용자 흐름을 그대로 나타내므로 별도 전역 상태를 만들지 않고
 * pathname에서 현재 단계만 계산한다. `/orders`와 `/checkout`은 모두 주문 단계다.
 */
export function BookingHeader() {
    const pathname = usePathname();
    let activeStep: BookingStep = "checkout";

    if (pathname.includes("/queue")) {
        activeStep = "queue";
    } else if (pathname.includes("/seats")) {
        activeStep = "seats";
    } else if (pathname.includes("/payments")) {
        activeStep = "payment";
    }

    return (
        <header
            className="grid min-h-[78px] grid-cols-[auto_auto_auto_1fr] items-center gap-2 border-b border-border bg-surface px-[var(--gutter-desktop)] text-[11px] text-muted-foreground max-sm:min-h-16 max-sm:grid-cols-1 max-sm:px-[var(--gutter-mobile)]">
            <Link className="max-sm:hidden" href="/games">
                홈
            </Link>
            <span aria-hidden="true" className="max-sm:hidden">
        ›
      </span>
            <strong className="text-foreground max-sm:hidden">
                {STEP_LABELS[activeStep]}
            </strong>
            <BookingProgress activeStep={activeStep}/>
        </header>
    );
}
