"use client";

import {type FormEvent, useState} from "react";

import {Button} from "@/components/ui/button";
import type {AdminGameRegisterRequest, AdminGameRegisterResponse} from "@/types/admin";

const teams = [
    [1, "두산 베어스"], [2, "LG 트윈스"], [3, "키움 히어로즈"],
    [4, "SSG 랜더스"], [5, "KT 위즈"], [6, "삼성 라이온즈"],
    [7, "NC 다이노스"], [8, "롯데 자이언츠"], [9, "KIA 타이거즈"],
    [10, "한화 이글스"],
] as const;

const stadiums = [
    [1, "서울종합운동장 야구장"], [2, "고척스카이돔"], [3, "인천SSG랜더스필드"],
    [4, "수원KT위즈파크"], [5, "대구삼성라이온즈파크"], [6, "창원NC파크"],
    [7, "사직야구장"], [8, "광주-기아 챔피언스필드"], [9, "대전 한화생명 볼파크"],
] as const;

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

function toApiDateTime(value: string) {
    const normalized = value.length === 16 ? `${value}:00` : value;
    return normalized.replace("T", " ");
}

export function AdminGameRegisterForm({
    isRegistering,
    onCancel,
    onRegister,
}: {
    isRegistering: boolean;
    onCancel: () => void;
    onRegister: (request: AdminGameRegisterRequest) => Promise<AdminGameRegisterResponse>;
}) {
    const [stadiumId, setStadiumId] = useState("");
    const [homeTeamId, setHomeTeamId] = useState("");
    const [awayTeamId, setAwayTeamId] = useState("");
    const [gameAt, setGameAt] = useState("");
    const [bookingOpenAt, setBookingOpenAt] = useState("");
    const [bookingCloseAt, setBookingCloseAt] = useState("");
    const [title, setTitle] = useState("");
    const [registeredGameId, setRegisteredGameId] = useState<number | null>(null);
    const hasRequiredValues = Boolean(stadiumId && homeTeamId && awayTeamId && gameAt && bookingOpenAt && bookingCloseAt && title.trim());
    const hasValidTeams = homeTeamId !== awayTeamId;
    const hasValidSchedule = bookingOpenAt < bookingCloseAt && bookingCloseAt <= gameAt;

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const response = await onRegister({
            stadiumId: Number(stadiumId),
            homeTeamId: Number(homeTeamId),
            awayTeamId: Number(awayTeamId),
            gameAt: toApiDateTime(gameAt),
            bookingOpenAt: toApiDateTime(bookingOpenAt),
            bookingCloseAt: toApiDateTime(bookingCloseAt),
            title: title.trim() || undefined,
        });
        setRegisteredGameId(response.gameId);
    };

    return (
        <section className="grid gap-4 rounded-control border border-border bg-muted/20 p-5">
            <header>
                <h3 className="font-black">경기 등록</h3>
                <p className="mt-1 text-xs text-muted-foreground">경기 등록 후 좌석 재고는 별도로 생성해야 합니다.</p>
            </header>
            {registeredGameId !== null && <p className="text-sm font-bold text-success">경기 #{registeredGameId}을 등록했습니다.</p>}
            <form className="grid gap-3 md:grid-cols-2 xl:grid-cols-3" onSubmit={submit}>
                <label className="grid gap-1 text-xs font-bold">홈팀
                    <select className={controlClassName} onChange={(event) => setHomeTeamId(event.target.value)} required value={homeTeamId}>
                        <option value="">선택</option>
                        {teams.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                    </select>
                </label>
                <label className="grid gap-1 text-xs font-bold">원정팀
                    <select className={controlClassName} onChange={(event) => setAwayTeamId(event.target.value)} required value={awayTeamId}>
                        <option value="">선택</option>
                        {teams.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                    </select>
                </label>
                <label className="grid gap-1 text-xs font-bold">구장
                    <select className={controlClassName} onChange={(event) => setStadiumId(event.target.value)} required value={stadiumId}>
                        <option value="">선택</option>
                        {stadiums.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
                    </select>
                </label>
                <label className="grid gap-1 text-xs font-bold">경기 일시
                    <input className={controlClassName} onChange={(event) => setGameAt(event.target.value)} required type="datetime-local" value={gameAt}/>
                </label>
                <label className="grid gap-1 text-xs font-bold">예매 오픈 일시
                    <input className={controlClassName} max={bookingCloseAt || undefined} onChange={(event) => setBookingOpenAt(event.target.value)} required type="datetime-local" value={bookingOpenAt}/>
                </label>
                <label className="grid gap-1 text-xs font-bold">예매 마감 일시
                    <input className={controlClassName} max={gameAt || undefined} min={bookingOpenAt || undefined} onChange={(event) => setBookingCloseAt(event.target.value)} required type="datetime-local" value={bookingCloseAt}/>
                </label>
                <label className="grid gap-1 text-xs font-bold md:col-span-2 xl:col-span-3">경기 제목
                    <input className={controlClassName} maxLength={255} onChange={(event) => setTitle(event.target.value)} placeholder="경기 제목" required value={title}/>
                </label>
                {!hasValidTeams && homeTeamId && awayTeamId && <p className="text-xs font-bold text-destructive md:col-span-2 xl:col-span-3">홈팀과 원정팀은 달라야 합니다.</p>}
                {!hasValidSchedule && gameAt && bookingOpenAt && bookingCloseAt && <p className="text-xs font-bold text-destructive md:col-span-2 xl:col-span-3">예매 오픈, 예매 마감, 경기 일시 순서로 입력해 주세요.</p>}
                <div className="flex gap-2 md:col-span-2 xl:col-span-3">
                    <Button disabled={!hasRequiredValues || !hasValidTeams || !hasValidSchedule} loading={isRegistering} type="submit">등록</Button>
                    <Button onClick={onCancel} type="button" variant="ghost">닫기</Button>
                </div>
            </form>
        </section>
    );
}
