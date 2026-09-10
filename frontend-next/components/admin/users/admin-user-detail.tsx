"use client";

import {ArrowLeft} from "lucide-react";
import {useState} from "react";

import {AdminUserTickets} from "@/components/admin/users/admin-user-tickets";
import {Alert} from "@/components/common/alert";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {useAdminUserDetail} from "@/hooks/use-admin-users";
import type {UserStatus} from "@/types/admin";
import type {UserRole} from "@/types/auth";

const selectClassName = "h-10 rounded-control border border-border bg-background px-3 text-sm";

export function AdminUserDetail({userId, onBack}: {userId: number; onBack: () => void}) {
    const detail = useAdminUserDetail(userId);
    const [role, setRole] = useState<UserRole | null>(null);
    const [status, setStatus] = useState<UserStatus | null>(null);

    if (detail.isLoading) {
        return <p className="py-16 text-center text-muted-foreground">회원 상세를 불러오고 있습니다...</p>;
    }

    if (!detail.user || !detail.tickets) {
        return (
            <div className="grid gap-5">
                <Button onClick={onBack} size="sm" type="button" variant="ghost">
                    <ArrowLeft aria-hidden="true"/>
                    회원 목록
                </Button>
                <Alert
                    message={detail.error?.message ?? "회원 상세 정보를 불러오지 못했습니다."}
                    variant="error"
                />
            </div>
        );
    }

    const user = detail.user;

    return (
        <div className="grid gap-8">
            <div>
                <Button onClick={onBack} size="sm" type="button" variant="ghost">
                    <ArrowLeft aria-hidden="true"/>
                    회원 목록
                </Button>
            </div>
            {detail.error && <Alert message={detail.error.message} variant="error"/>}
            <section className="grid gap-5 border-b border-border pb-8">
                <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-2xl font-black">{user.name ?? user.nickname ?? "이름 없음"}</h2>
                    <Badge variant={user.status === "ACTIVE" ? "success" : "secondary"}>{user.status}</Badge>
                </div>
                <dl className="grid gap-4 text-sm sm:grid-cols-2">
                    <div><dt className="text-xs font-bold text-muted-foreground">이메일</dt><dd className="mt-1">{user.email}</dd></div>
                    <div><dt className="text-xs font-bold text-muted-foreground">전화번호</dt><dd className="mt-1">{user.phone ?? "-"}</dd></div>
                    <div><dt className="text-xs font-bold text-muted-foreground">본인인증</dt><dd className="mt-1">{user.isVerified ? "완료" : "미완료"}</dd></div>
                    <div><dt className="text-xs font-bold text-muted-foreground">가입일</dt><dd className="mt-1">{user.createdAt.slice(0, 10)}</dd></div>
                </dl>
            </section>
            <section className="grid gap-4 border-b border-border pb-8">
                <h3 className="font-black">계정 권한 및 상태</h3>
                <div className="flex flex-wrap gap-3">
                    <select aria-label="회원 권한" className={selectClassName} onChange={(event) => setRole(event.target.value as UserRole)} value={role ?? user.role}>
                        <option value="USER">일반 회원</option><option value="ADMIN">관리자</option>
                    </select>
                    <Button disabled={!role || role === user.role} loading={detail.isUpdatingRole} onClick={() => role && void detail.updateRole(role)} type="button" variant="outline">권한 변경</Button>
                    <select aria-label="회원 상태" className={selectClassName} onChange={(event) => setStatus(event.target.value as UserStatus)} value={status ?? user.status}>
                        <option value="ACTIVE">활성</option><option value="SUSPENDED">정지</option><option value="DELETED">탈퇴</option>
                    </select>
                    <Button disabled={!status || status === user.status} loading={detail.isUpdatingStatus} onClick={() => status && void detail.updateStatus(status)} type="button" variant="outline">상태 변경</Button>
                </div>
            </section>
            <section>
                <h3 className="font-black">보유 티켓</h3>
                <AdminUserTickets
                    isCanceling={detail.isCancelingTicket}
                    onCancel={detail.cancelTicket}
                    tickets={detail.tickets.content}
                />
            </section>
        </div>
    );
}
