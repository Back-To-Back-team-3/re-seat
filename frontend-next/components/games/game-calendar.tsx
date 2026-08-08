"use client";

import type { ReactNode } from "react";
import { useState } from "react";

import { KST_TIME_ZONE } from "@/lib/constants";
import type { GameSummary } from "@/types/game";

const LEGEND_ITEMS: Array<{
  status: GameSummary["bookingStatus"];
  label: string;
  dotClassName: string;
}> = [
  { status: "SCHEDULED", label: "예매 예정", dotClassName: "bg-[#2b67cb]" },
  { status: "OPEN", label: "예매중", dotClassName: "bg-success" },
  { status: "CLOSED", label: "예매 종료", dotClassName: "bg-[#747b8d]" },
  { status: "CANCELLED", label: "경기 취소", dotClassName: "bg-brand" },
];

type GameCalendarProps = {
  games: GameSummary[];
  selectedDate: string | null;
  onSelectDate: (date: string | null) => void;
  /** 구단·구장·상태 필터. Vite에서 캘린더 툴바 안, 월 이동 컨트롤 옆에 위치한다. */
  filters: ReactNode;
};

/**
 * 월 단위로 날짜별 경기 수와 예매 상태를 보여주는 일정 탐색 영역입니다.
 *
 * 백엔드 LocalDateTime은 KST 기준이므로 날짜 비교에는 앞 10자리의 날짜 값을
 * 사용합니다. 달을 이동하면 특정 날짜 선택을 해제해 다른 달의 빈 결과가 남지
 * 않게 하며, 오늘 버튼은 KST 기준 현재 월과 날짜로 돌아갑니다.
 */
export function GameCalendar({
  games,
  selectedDate,
  onSelectDate,
  filters,
}: GameCalendarProps) {
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: KST_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  const [todayYear, todayMonth] = today.split("-").map(Number);
  const [cursor, setCursor] = useState(() => ({
    year: todayYear,
    month: todayMonth - 1,
  }));
  const firstWeekday = new Date(
    Date.UTC(cursor.year, cursor.month, 1),
  ).getUTCDay();
  const lastDate = new Date(
    Date.UTC(cursor.year, cursor.month + 1, 0),
  ).getUTCDate();
  const cells: Array<number | null> = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: lastDate }, (_, index) => index + 1),
  ];

  function moveMonth(offset: number) {
    const next = new Date(Date.UTC(cursor.year, cursor.month + offset, 1));
    setCursor({ year: next.getUTCFullYear(), month: next.getUTCMonth() });
    onSelectDate(null);
  }

  function dateKey(day: number) {
    return `${cursor.year}-${String(cursor.month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  return (
    <section
      aria-label="경기 날짜 선택"
      className="overflow-hidden rounded-[18px] border border-border bg-surface shadow-card"
    >
      <div className="flex items-end justify-between gap-6 border-b border-border px-[22px] py-5 max-[1024px]:flex-col max-[1024px]:items-stretch">
        <div className="flex items-center gap-2 whitespace-nowrap">
          <button
            aria-label="이전 달"
            className="grid size-9 cursor-pointer place-items-center rounded-[9px] border border-border bg-surface"
            onClick={() => moveMonth(-1)}
            type="button"
          >
            ‹
          </button>
          <strong className="min-w-[120px] text-center text-lg">
            {cursor.year}년 {cursor.month + 1}월
          </strong>
          <button
            aria-label="다음 달"
            className="grid size-9 cursor-pointer place-items-center rounded-[9px] border border-border bg-surface"
            onClick={() => moveMonth(1)}
            type="button"
          >
            ›
          </button>
          <button
            className="cursor-pointer px-3 text-[13px] font-bold text-brand"
            onClick={() => {
              setCursor({ year: todayYear, month: todayMonth - 1 });
              onSelectDate(today);
            }}
            type="button"
          >
            오늘
          </button>
        </div>
        <div className="flex flex-wrap justify-end gap-2.5 max-[1024px]:justify-start">
          {filters}
        </div>
      </div>
      <div className="grid grid-cols-7 border-b border-border bg-surface-soft text-center text-xs font-bold text-muted-foreground">
        {["일", "월", "화", "수", "목", "금", "토"].map((weekday) => (
          <span className="py-[9px]" key={weekday}>
            {weekday}
          </span>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {cells.map((day, index) => {
          if (!day) {
            return (
              <span
                aria-hidden="true"
                className="min-h-20 border-r border-b border-border bg-muted/20 [&:nth-child(7n)]:border-r-0"
                key={`empty-${index}`}
              />
            );
          }

          const date = dateKey(day);
          const dayGames = games.filter((game) =>
            game.gameAt.startsWith(date),
          );

          return (
            <button
              aria-label={`${date} 경기 ${dayGames.length}개`}
              aria-pressed={selectedDate === date}
              className={`grid min-h-20 cursor-pointer content-start gap-1 border-0 border-r border-b border-border p-2 text-left [&:nth-child(7n)]:border-r-0 ${
                selectedDate === date
                  ? "bg-brand text-white"
                  : date === today
                    ? "bg-brand/10 text-foreground"
                    : "bg-background text-foreground"
              }`}
              key={date}
              onClick={() =>
                onSelectDate(selectedDate === date ? null : date)
              }
              type="button"
            >
              <strong>{day}</strong>
              {dayGames.length > 0 && (
                <>
                  <small>{dayGames.length}경기</small>
                  <span className="flex gap-1" aria-hidden="true">
                    {dayGames.map((game) => (
                      <i
                        className="size-1.5 rounded-full bg-current"
                        key={game.gameId}
                      />
                    ))}
                  </span>
                </>
              )}
            </button>
          );
        })}
      </div>
      <ul
        aria-label="예매 상태 범례"
        className="flex flex-wrap justify-end gap-3.5 px-5 py-3 text-[11px] text-muted-foreground max-[640px]:justify-start"
      >
        {LEGEND_ITEMS.map((item) => (
          <li className="flex items-center gap-1.5" key={item.status}>
            <i
              aria-hidden="true"
              className={`block size-2 rounded-full ${item.dotClassName}`}
            />
            {item.label}
          </li>
        ))}
      </ul>
    </section>
  );
}
