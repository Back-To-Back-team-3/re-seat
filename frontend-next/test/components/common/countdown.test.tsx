import {act, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {Countdown} from "@/components/common/countdown";

describe("Countdown", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("남은 시간을 분과 초로 표시하고 1초마다 갱신한다", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-08-07T00:00:00.000Z"));

        render(<Countdown target="2026-08-07T00:01:05.000Z"/>);

        expect(screen.getByText("1:05")).toBeInTheDocument();

        // 실제 시간을 기다리지 않고 타이머만 1초 진행해 화면 갱신 시점을 검증한다.
        act(() => {
            vi.advanceTimersByTime(1_000);
        });

        expect(screen.getByText("1:04")).toBeInTheDocument();
    });

    it("제한 시간이 끝나면 만료를 표시하고 콜백을 한 번 호출한다", () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-08-07T00:00:00.000Z"));
        const onExpire = vi.fn();

        render(
            <Countdown
                target="2026-08-07T00:00:01.000Z"
                onExpire={onExpire}
            />,
        );

        act(() => {
            vi.advanceTimersByTime(1_000);
        });

        expect(screen.getByText("만료")).toBeInTheDocument();
        expect(onExpire).toHaveBeenCalledTimes(1);

        // interval이 계속 실행되더라도 같은 만료를 반복해서 알리지 않아야 한다.
        act(() => {
            vi.advanceTimersByTime(2_000);
        });

        expect(onExpire).toHaveBeenCalledTimes(1);
    });
});
