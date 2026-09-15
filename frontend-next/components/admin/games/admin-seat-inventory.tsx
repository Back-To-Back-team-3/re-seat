"use client";

import {ShieldBan, ShieldCheck} from "lucide-react";
import {useState} from "react";

import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminSeatInventory} from "@/hooks/use-admin-seat-inventory";
import type {GameSeat, GameSeatStatus} from "@/types/game";

const summaryItems = [
    ["available", "판매 가능"],
    ["held", "선점"],
    ["sold", "판매 완료"],
    ["blocked", "차단"],
] as const;

const statusLabels: Record<GameSeatStatus, string> = {
    AVAILABLE: "판매 가능",
    HELD: "선점",
    SOLD: "판매 완료",
    BLOCKED: "차단",
};

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminSeatInventory({gameId}: {gameId: number}) {
    const [status, setStatus] = useState<GameSeatStatus | "">("");
    const [selectedSeat, setSelectedSeat] = useState<GameSeat | null>(null);
    const [reason, setReason] = useState("");
    const inventory = useAdminSeatInventory(gameId, status || undefined);
    const summary = inventory.summary;
    const action = selectedSeat?.status === "BLOCKED" ? "unblock" : "block";
    const mutable =
        selectedSeat?.status === "AVAILABLE" || selectedSeat?.status === "BLOCKED";

    const updateSeat = async () => {
        if (!selectedSeat || !mutable || !reason.trim()) return;
        await inventory.updateStatus(selectedSeat.gameSeatId, action, reason.trim());
        setSelectedSeat(null);
        setReason("");
    };

    return (
        <section className="grid gap-4 rounded-control border border-border bg-muted/20 p-5">
            <header className="flex flex-wrap items-end justify-between gap-3">
                <div>
                    <h3 className="font-black">좌석 재고</h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                        상태별 재고를 확인하고 판매 가능 좌석을 차단하거나 해제합니다.
                    </p>
                </div>
                <label className="grid gap-1 text-xs font-bold">
                    좌석 상태
                    <select
                        className={controlClassName}
                        onChange={(event) => {
                            setStatus(event.target.value as GameSeatStatus | "");
                            setSelectedSeat(null);
                        }}
                        value={status}
                    >
                        <option value="">전체</option>
                        {Object.entries(statusLabels).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                        ))}
                    </select>
                </label>
            </header>

            {inventory.error && <Alert message={inventory.error.message} variant="error"/>}
            {summary && (
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                    {summaryItems.map(([key, label]) => (
                        <div className="rounded-control border border-border bg-surface p-3" key={key}>
                            <p className="text-xs text-muted-foreground">{label}</p>
                            <strong className="mt-1 block font-mono text-lg">
                                {summary[key].toLocaleString()}
                            </strong>
                        </div>
                    ))}
                </div>
            )}

            {inventory.isLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">좌석 재고를 불러오고 있습니다...</p>
            ) : (
                <div className="grid max-h-[360px] grid-cols-2 gap-2 overflow-y-auto sm:grid-cols-4 lg:grid-cols-6">
                    {inventory.seats.map((seat) => (
                        <button
                            className={`rounded-control border p-2 text-left text-xs ${
                                selectedSeat?.gameSeatId === seat.gameSeatId
                                    ? "border-brand bg-brand/5"
                                    : "border-border bg-surface"
                            }`}
                            key={seat.gameSeatId}
                            onClick={() => {
                                setSelectedSeat(seat);
                                setReason("");
                            }}
                            type="button"
                        >
                            <strong>{seat.zoneName} · {seat.seatRow}열 {seat.seatNumber}번</strong>
                            <span className="mt-1 block text-muted-foreground">{statusLabels[seat.status]}</span>
                        </button>
                    ))}
                </div>
            )}

            {selectedSeat && (
                <div className="grid gap-3 border-t border-border pt-4">
                    <p className="text-sm font-bold">
                        {selectedSeat.zoneName} · {selectedSeat.seatRow}열 {selectedSeat.seatNumber}번
                    </p>
                    {mutable ? (
                        <div className="flex flex-col gap-2 sm:flex-row">
                            <input
                                className={`${controlClassName} flex-1`}
                                maxLength={255}
                                onChange={(event) => setReason(event.target.value)}
                                placeholder={`${action === "block" ? "차단" : "해제"} 사유`}
                                value={reason}
                            />
                            <Button
                                disabled={!reason.trim()}
                                loading={inventory.isUpdating}
                                onClick={() => void updateSeat()}
                                type="button"
                                variant={action === "block" ? "destructive" : "outline"}
                            >
                                {action === "block" ? <ShieldBan aria-hidden="true"/> : <ShieldCheck aria-hidden="true"/>}
                                {action === "block" ? "판매 차단" : "차단 해제"}
                            </Button>
                        </div>
                    ) : (
                        <p className="text-xs text-muted-foreground">선점 또는 판매 완료 좌석은 상태를 변경할 수 없습니다.</p>
                    )}
                </div>
            )}
        </section>
    );
}
