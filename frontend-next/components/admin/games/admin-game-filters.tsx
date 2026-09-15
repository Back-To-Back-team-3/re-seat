"use client";

import {Search} from "lucide-react";
import {type FormEvent, useState} from "react";

import {Button} from "@/components/ui/button";
import type {AdminGameSearchCondition} from "@/types/admin";
import type {GameSummary} from "@/types/game";

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminGameFilters({
    onSearch,
}: {
    onSearch: (condition: AdminGameSearchCondition) => void;
}) {
    const [homeTeamId, setHomeTeamId] = useState("");
    const [awayTeamId, setAwayTeamId] = useState("");
    const [stadiumId, setStadiumId] = useState("");
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [bookingStatus, setBookingStatus] = useState<GameSummary["bookingStatus"] | "">("");

    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onSearch({
            homeTeamId: homeTeamId ? Number(homeTeamId) : undefined,
            awayTeamId: awayTeamId ? Number(awayTeamId) : undefined,
            stadiumId: stadiumId ? Number(stadiumId) : undefined,
            from: from || undefined,
            to: to || undefined,
            bookingStatus: bookingStatus || undefined,
        });
    };

    return (
        <form className="grid gap-3 border-b border-border pb-5 md:grid-cols-3" onSubmit={submit}>
            <label className="grid gap-1 text-xs font-bold">
                홈팀 ID
                <input
                    className={controlClassName}
                    min="1"
                    onChange={(event) => setHomeTeamId(event.target.value)}
                    placeholder="전체"
                    step="1"
                    type="number"
                    value={homeTeamId}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                원정팀 ID
                <input
                    className={controlClassName}
                    min="1"
                    onChange={(event) => setAwayTeamId(event.target.value)}
                    placeholder="전체"
                    step="1"
                    type="number"
                    value={awayTeamId}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                구장 ID
                <input
                    className={controlClassName}
                    min="1"
                    onChange={(event) => setStadiumId(event.target.value)}
                    placeholder="전체"
                    step="1"
                    type="number"
                    value={stadiumId}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                경기 시작일
                <input
                    className={controlClassName}
                    max={to || undefined}
                    onChange={(event) => setFrom(event.target.value)}
                    type="date"
                    value={from}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                경기 종료일
                <input
                    className={controlClassName}
                    min={from || undefined}
                    onChange={(event) => setTo(event.target.value)}
                    type="date"
                    value={to}
                />
            </label>
            <label className="grid gap-1 text-xs font-bold">
                예매 상태
                <select
                    className={controlClassName}
                    onChange={(event) => setBookingStatus(event.target.value as GameSummary["bookingStatus"] | "")}
                    value={bookingStatus}
                >
                    <option value="">전체</option>
                    <option value="SCHEDULED">예정</option>
                    <option value="OPEN">예매 중</option>
                    <option value="CLOSED">예매 마감</option>
                    <option value="CANCELLED">취소</option>
                </select>
            </label>
            <Button className="justify-self-start md:col-span-3" type="submit">
                <Search aria-hidden="true"/>
                조회
            </Button>
        </form>
    );
}
