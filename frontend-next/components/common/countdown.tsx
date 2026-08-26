"use client";

import {useEffect, useState} from "react";

import {parseApiDateTime} from "@/lib/date";

export type CountdownProps = {
    target?: string | null;
    onExpire?: () => void;
};

type CountdownState = {
    label: string;
    expired: boolean;
};

/**
 * 백엔드가 전달한 만료 시각까지 남은 시간을 `분:초` 형식으로 표시한다.
 *
 * 유효하지 않거나 없는 시각은 `-`로 표시하고, 시간이 끝난 순간에는
 * 기존 Vite 화면과 동일하게 `만료` 문구와 onExpire 알림을 제공한다.
 */
export function Countdown({target, onExpire}: CountdownProps) {
    const [now, setNow] = useState(() => Date.now());
    const countdown = getCountdown(target, now);

    useEffect(() => {
        // effect는 브라우저 타이머 구독만 담당한다. 표시 문구는 target과 now로
        // 렌더링할 때 계산하여 같은 데이터를 state에 중복 저장하지 않는다.
        const timerId = window.setInterval(() => {
            setNow(Date.now());
        }, 1_000);

        return () => {
            window.clearInterval(timerId);
        };
    }, []);

    useEffect(() => {
        if (countdown.expired) {
            onExpire?.();
        }
    }, [countdown.expired, onExpire]);

    return (
        <span
            className={
                countdown.expired
                    ? "font-sans text-[22px] font-black tracking-[-0.03em] text-muted-foreground tabular-nums"
                    : "font-sans text-[22px] font-black tracking-[-0.03em] text-brand tabular-nums"
            }
        >
      {countdown.label}
    </span>
    );
}

function getCountdown(
    target: string | null | undefined,
    now: number,
): CountdownState {
    const targetDate = parseApiDateTime(target);

    if (!targetDate) {
        return {label: "-", expired: false};
    }

    const totalSeconds = Math.max(
        0,
        Math.ceil((targetDate.getTime() - now) / 1_000),
    );

    if (totalSeconds === 0) {
        return {label: "만료", expired: true};
    }

    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    return {
        label: `${minutes}:${seconds.toString().padStart(2, "0")}`,
        expired: false,
    };
}
