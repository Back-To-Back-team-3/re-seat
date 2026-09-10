"use client";

import {Boxes, CalendarCog} from "lucide-react";
import {useState} from "react";

import {Button} from "@/components/ui/button";
import type {GameSummary} from "@/types/game";

type MutableBookingStatus = Exclude<GameSummary["bookingStatus"], "SCHEDULED">;

export function AdminGameOperations({
    game,
    onUpdateStatus,
    onOpenInventory,
    isUpdatingStatus,
    isOpeningInventory,
}: {
    game: GameSummary;
    onUpdateStatus: (status: MutableBookingStatus, reason: string) => Promise<unknown> | void;
    onOpenInventory: () => Promise<unknown> | void;
    isUpdatingStatus: boolean;
    isOpeningInventory: boolean;
}) {
    const [status, setStatus] = useState<MutableBookingStatus>(game.bookingStatus === "SCHEDULED" ? "OPEN" : game.bookingStatus);
    const [reason, setReason] = useState("");
    const [confirmInventory, setConfirmInventory] = useState(false);
    const [actionMessage, setActionMessage] = useState<string | null>(null);
    const controlClassName = "h-10 rounded-control border border-border bg-background px-3 text-sm";

    return (
        <section className="grid gap-5 border-t border-border pt-6">
            <header>
                <p className="text-xs font-bold text-brand">SELECTED GAME</p>
                <h3 className="mt-1 text-lg font-black">{game.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{game.stadium.name} · 현재 {game.bookingStatus}</p>
            </header>
            {actionMessage && <p className="text-sm font-bold text-success">{actionMessage}</p>}
            <div className="grid gap-3 lg:grid-cols-[180px_1fr_auto]">
                <label className="grid gap-1 text-xs font-bold">변경할 예매 상태
                    <select className={controlClassName} onChange={(event) => setStatus(event.target.value as MutableBookingStatus)} value={status}>
                        <option value="OPEN">OPEN</option><option value="CLOSED">CLOSED</option><option value="CANCELLED">CANCELLED</option>
                    </select>
                </label>
                <label className="grid gap-1 text-xs font-bold">상태 변경 사유
                    <input className={controlClassName} onChange={(event) => setReason(event.target.value)} placeholder="운영 사유를 입력하세요" value={reason}/>
                </label>
                <Button
                    className="self-end"
                    disabled={!reason.trim() || status === game.bookingStatus}
                    loading={isUpdatingStatus}
                    onClick={async () => {
                        await onUpdateStatus(status, reason.trim());
                        setReason("");
                        setActionMessage("예매 상태를 변경했습니다.");
                    }}
                    type="button"
                    variant="outline"
                >
                    <CalendarCog aria-hidden="true"/>
                    예매 상태 변경
                </Button>
            </div>
            <div className="flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between">
                <div><h4 className="font-bold">좌석 재고 생성</h4><p className="mt-1 text-xs text-muted-foreground">경기 좌석 재고가 아직 생성되지 않은 경우에만 실행하세요.</p></div>
                {confirmInventory ? (
                    <div className="flex gap-2">
                        <Button
                            loading={isOpeningInventory}
                            onClick={async () => {
                                await onOpenInventory();
                                setConfirmInventory(false);
                                setActionMessage("좌석 재고 생성을 완료했습니다.");
                            }}
                            size="sm"
                            type="button"
                        >
                            생성 확인
                        </Button>
                        <Button onClick={() => setConfirmInventory(false)} size="sm" type="button" variant="ghost">취소</Button>
                    </div>
                ) : (
                    <Button onClick={() => setConfirmInventory(true)} size="sm" type="button" variant="outline"><Boxes aria-hidden="true"/>좌석 재고 생성</Button>
                )}
            </div>
        </section>
    );
}
