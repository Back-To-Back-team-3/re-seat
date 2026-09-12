import type {ReactNode} from "react";

import {cn} from "@/lib/utils";

interface BookingPanelHeaderProps {
    step: string;
    title: string;
    description: ReactNode;
    className?: string;
}

/** 예매 단계 패널의 순서와 역할을 일관된 형식으로 안내한다. */
export function BookingPanelHeader({
                                       step,
                                       title,
                                       description,
                                       className,
                                   }: BookingPanelHeaderProps) {
    return (
        <div
            className={cn(
                "flex items-center gap-2.5 border-b border-border px-[18px] py-[17px]",
                className,
            )}
        >
            <span className="text-xs font-black tracking-[0.1em] text-brand">
                {step}
            </span>
            <div className="grid gap-0.5">
                <strong className="text-[13px]">{title}</strong>
                <small className="text-xs text-muted-foreground">
                    {description}
                </small>
            </div>
        </div>
    );
}
