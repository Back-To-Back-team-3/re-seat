"use client";

import {ChevronLeft, ChevronRight, QrCode, Search} from "lucide-react";
import {type FormEvent, useState} from "react";

import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminTickets} from "@/hooks/use-admin-tickets";
import type {
    AdminTicketBulkCancelResponse,
    AdminTicketSearchCondition,
    AdminTicketVerifyResponse,
} from "@/types/admin";
import type {TicketStatus} from "@/types/ticket";

const TICKETS_PER_PAGE = 10;

const ticketStatuses: Array<[TicketStatus, string]> = [
    ["ISSUED", "발급"],
    ["REFUND_PENDING", "환불 진행"],
    ["REFUND_FAILED", "환불 실패"],
    ["REFUNDED", "환불 완료"],
    ["USED_ENTERED", "입장 완료"],
    ["USED_NO_SHOW", "미입장 종료"],
];

const controlClassName =
    "h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary focus:ring-3 focus:ring-ring/20";

export function AdminTicketManagement() {
    const [condition, setCondition] = useState<AdminTicketSearchCondition>({});
    const [page, setPage] = useState(0);
    const [selectedTicketId, setSelectedTicketId] = useState<number | null>(null);
    const [cancelReason, setCancelReason] = useState("");
    const [operationMessage, setOperationMessage] = useState<string | null>(null);
    const [reissuedQrToken, setReissuedQrToken] = useState<string | null>(null);
    const [verifyResult, setVerifyResult] = useState<AdminTicketVerifyResponse | null>(null);
    const [bulkResult, setBulkResult] = useState<AdminTicketBulkCancelResponse | null>(null);
    const adminTickets = useAdminTickets(condition, page, TICKETS_PER_PAGE);
    const result = adminTickets.tickets;

    const search = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const userId = String(form.get("userId") ?? "");
        const status = String(form.get("status") ?? "") as TicketStatus | "";
        const gameDateFrom = String(form.get("gameDateFrom") ?? "");
        const gameDateTo = String(form.get("gameDateTo") ?? "");
        setCondition({
            userId: userId ? Number(userId) : undefined,
            status: status || undefined,
            gameDateFrom: gameDateFrom || undefined,
            gameDateTo: gameDateTo || undefined,
        });
        setPage(0);
        setSelectedTicketId(null);
        setCancelReason("");
    };

    const cancelTicket = async (ticketId: number) => {
        try {
            await adminTickets.cancelTicket(ticketId, cancelReason.trim());
            setSelectedTicketId(null);
            setCancelReason("");
            setOperationMessage(`티켓 #${ticketId}의 취소를 접수했습니다.`);
        } catch {
            return;
        }
    };

    const reissueQr = async (ticketId: number) => {
        try {
            const response = await adminTickets.reissueQr(ticketId);
            setReissuedQrToken(response.qrToken);
            setOperationMessage(`티켓 #${ticketId}의 QR을 재발급했습니다.`);
        } catch {
            return;
        }
    };

    const verifyTicket = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const gameId = Number(form.get("gameId"));
        const qrToken = String(form.get("qrToken") ?? "").trim();
        try {
            const response = await adminTickets.verifyTicket(gameId, qrToken);
            setVerifyResult(response);
            setOperationMessage(`티켓 #${response.ticketId}의 입장을 처리했습니다.`);
        } catch {
            return;
        }
    };

    const cancelGameTickets = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const gameId = Number(form.get("gameId"));
        const reason = String(form.get("reason") ?? "").trim();
        try {
            const response = await adminTickets.cancelGameTickets(gameId, reason);
            setBulkResult(response);
            setOperationMessage(`경기 #${gameId} 티켓 일괄 취소 처리를 완료했습니다.`);
        } catch {
            return;
        }
    };

    return (
        <section aria-labelledby="admin-tickets-tab" className="grid gap-6 p-5 sm:p-8 md:p-10" id="admin-tickets-panel" role="tabpanel">
            <header>
                <p className="text-xs font-extrabold text-brand">TICKETS</p>
                <h2 className="mt-1 text-2xl font-black">티켓 관리</h2>
                <p className="mt-2 text-sm text-muted-foreground">티켓을 검색하고 QR 검표·재발급과 취소 작업을 처리합니다.</p>
            </header>

            {adminTickets.error && <Alert message={adminTickets.error.message} variant="error"/>}
            {operationMessage && <Alert message={operationMessage} onClose={() => setOperationMessage(null)} variant="success"/>}
            {reissuedQrToken && (
                <p className="break-all rounded-control border border-border bg-muted/30 p-3 font-mono text-xs">
                    재발급 QR: {reissuedQrToken}
                </p>
            )}

            <div className="grid gap-4 lg:grid-cols-2">
                <form className="grid gap-3 rounded-control border border-border bg-muted/20 p-4" onSubmit={verifyTicket}>
                    <div>
                        <h3 className="font-black">QR 검표</h3>
                        <p className="mt-1 text-xs text-muted-foreground">경기 ID와 QR 토큰을 확인해 입장을 처리합니다.</p>
                    </div>
                    <input className={controlClassName} min="1" name="gameId" placeholder="경기 ID" required type="number"/>
                    <input className={controlClassName} name="qrToken" placeholder="QR 토큰" required/>
                    <Button loading={adminTickets.isVerifying} type="submit"><QrCode aria-hidden="true"/>검표</Button>
                    {verifyResult && <p className="text-xs text-muted-foreground">{verifyResult.holderName ?? "이름 없음"} · {verifyResult.seat} · {verifyResult.status}</p>}
                </form>

                <form className="grid gap-3 rounded-control border border-destructive/30 bg-destructive/5 p-4" onSubmit={cancelGameTickets}>
                    <div>
                        <h3 className="font-black">경기 티켓 일괄 취소</h3>
                        <p className="mt-1 text-xs text-muted-foreground">해당 경기의 발급 상태 티켓을 모두 취소 접수합니다.</p>
                    </div>
                    <input className={controlClassName} min="1" name="gameId" placeholder="경기 ID" required type="number"/>
                    <input className={controlClassName} name="reason" placeholder="일괄 취소 사유" required/>
                    <Button loading={adminTickets.isBulkCanceling} type="submit" variant="destructive">일괄 취소</Button>
                    {bulkResult && <p className="text-xs text-muted-foreground">전체 {bulkResult.totalCount}건 · 성공 {bulkResult.successCount}건 · 실패 {bulkResult.failureCount}건</p>}
                </form>
            </div>

            <form className="grid gap-3 border-b border-border pb-5 md:grid-cols-4" onSubmit={search}>
                <input className={controlClassName} min="1" name="userId" placeholder="회원 ID" type="number"/>
                <select className={controlClassName} name="status">
                    <option value="">전체 상태</option>
                    {ticketStatuses.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
                <input className={controlClassName} name="gameDateFrom" type="date"/>
                <input className={controlClassName} name="gameDateTo" type="date"/>
                <Button className="justify-self-start md:col-span-4" type="submit"><Search aria-hidden="true"/>조회</Button>
            </form>

            {adminTickets.isLoading ? (
                <p className="py-16 text-center text-muted-foreground">티켓을 불러오고 있습니다...</p>
            ) : result?.content.length ? (
                <div className="grid gap-3">
                    {result.content.map((ticket) => (
                        <article className="grid gap-3 rounded-control border border-border p-4" key={ticket.ticketId}>
                            <div className="flex flex-wrap items-start justify-between gap-3">
                                <div>
                                    <strong>{ticket.gameTitle}</strong>
                                    <p className="mt-1 text-xs text-muted-foreground">{ticket.ticketNo} · {ticket.seat} · {ticket.status}</p>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                    <Button disabled={ticket.status !== "ISSUED"} loading={adminTickets.isReissuing} onClick={() => void reissueQr(ticket.ticketId)} size="sm" type="button" variant="outline">QR 재발급</Button>
                                    <Button disabled={ticket.status !== "ISSUED"} onClick={() => {
                                        setSelectedTicketId(ticket.ticketId);
                                        setCancelReason("");
                                    }} size="sm" type="button" variant="destructive">직권 취소</Button>
                                </div>
                            </div>
                            {selectedTicketId === ticket.ticketId && (
                                <div className="flex flex-col gap-2 border-t border-border pt-3 sm:flex-row">
                                    <input className={`${controlClassName} flex-1`} maxLength={255} onChange={(event) => setCancelReason(event.target.value)} placeholder="취소 사유" value={cancelReason}/>
                                    <Button disabled={!cancelReason.trim()} loading={adminTickets.isCanceling} onClick={() => void cancelTicket(ticket.ticketId)} size="sm" type="button" variant="destructive">취소 확정</Button>
                                    <Button onClick={() => {
                                        setSelectedTicketId(null);
                                        setCancelReason("");
                                    }} size="sm" type="button" variant="ghost">닫기</Button>
                                </div>
                            )}
                        </article>
                    ))}
                </div>
            ) : (
                <p className="py-16 text-center text-muted-foreground">조건에 맞는 티켓이 없습니다.</p>
            )}

            {result && (
                <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
                    <span className="text-muted-foreground">총 {result.totalElements.toLocaleString()}건</span>
                    <nav aria-label="티켓 목록 페이지" className="flex items-center gap-2">
                        <Button aria-label="이전 페이지" disabled={result.pageNumber === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} size="icon-sm" type="button" variant="outline"><ChevronLeft aria-hidden="true"/></Button>
                        <span>{result.pageNumber + 1} / {Math.max(result.totalPages, 1)} 페이지</span>
                        <Button aria-label="다음 페이지" disabled={result.pageNumber + 1 >= result.totalPages} onClick={() => setPage((value) => Math.min(result.totalPages - 1, value + 1))} size="icon-sm" type="button" variant="outline"><ChevronRight aria-hidden="true"/></Button>
                    </nav>
                </div>
            )}
        </section>
    );
}
