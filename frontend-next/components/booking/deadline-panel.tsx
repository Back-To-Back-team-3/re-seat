import type {ReactNode} from "react";

import {Countdown} from "@/components/common/countdown";
import {cn} from "@/lib/utils";

interface DeadlinePanelProps {
    label: ReactNode;
    target: string | null;
    expired: boolean;
    onExpire?: () => void;
    fallbackValue?: ReactNode;
    expiredMessage?: string;
    className?: string;
    messageClassName?: string;
}

/** 예매 단계의 남은 시간과 만료 안내를 같은 시각 규칙으로 표시한다. */
export function DeadlinePanel({
                                  label,
                                  target,
                                  expired,
                                  onExpire,
                                  fallbackValue,
                                  expiredMessage,
                                  className,
                                  messageClassName,
                              }: DeadlinePanelProps) {
    return (
        <>
            <div
                className={cn(
                    "flex items-center justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-sm font-bold",
                    expired || !target
                        ? "border-brand/40 bg-brand/12"
                        : "border-[color-mix(in_srgb,var(--brand)_28%,var(--border))] bg-brand/[0.07]",
                    className,
                )}
            >
                <span>{label}</span>
                {target ? (
                    <Countdown onExpire={onExpire} target={target}/>
                ) : (
                    <strong>{fallbackValue}</strong>
                )}
            </div>
            {expired && expiredMessage && (
                <p className={cn("text-xs font-bold text-brand", messageClassName)}>
                    {expiredMessage}
                </p>
            )}
        </>
    );
}
