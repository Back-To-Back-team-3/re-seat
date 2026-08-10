export type BookingStep = "queue" | "seats" | "checkout" | "payment";

const BOOKING_STEPS: Array<{ id: BookingStep; label: string }> = [
    {id: "queue", label: "예매 대기"},
    {id: "seats", label: "좌석 선택"},
    {id: "checkout", label: "주문"},
    {id: "payment", label: "결제"},
];

/**
 * 대기열부터 결제까지 현재 위치와 완료한 단계를 표시한다.
 */
export function BookingProgress({activeStep}: { activeStep: BookingStep }) {
    const activeIndex = BOOKING_STEPS.findIndex(
        (step) => step.id === activeStep,
    );

    return (
        <ol
            aria-label="예매 진행 단계"
            className="flex justify-end gap-6.5 max-sm:justify-between max-sm:gap-2"
        >
            {BOOKING_STEPS.map((step, index) => {
                const isDone = index < activeIndex;
                const isActive = index === activeIndex;

                return (
                    <li
                        aria-current={isActive ? "step" : undefined}
                        className={[
                            "flex items-center gap-1.75 text-muted-foreground",
                            "max-sm:flex-1 max-sm:after:h-px max-sm:after:w-full max-sm:after:bg-border max-sm:last:after:hidden",
                            isActive || isDone ? "text-foreground" : "",
                        ].join(" ")}
                        key={step.id}
                    >
            <span
                aria-hidden="true"
                className={[
                    "grid size-6 shrink-0 place-items-center rounded-full border border-border font-mono text-[9px]",
                    isActive ? "border-brand bg-brand text-white" : "",
                    isDone ? "border-success text-success" : "",
                ].join(" ")}
            >
              {isDone ? "✓" : index + 1}
            </span>
                        {/* Vite styles.css는 .booking-progress strong을 10px로 선언하지만
                뒤쪽 "2026 UI readability" 구간이 같은 선택자를 12px로 다시
                선언해 실제로 적용되는 값은 12px다. */}
                        <strong className="text-xs max-[900px]:hidden">
                            {step.label}
                        </strong>
                    </li>
                );
            })}
        </ol>
    );
}
