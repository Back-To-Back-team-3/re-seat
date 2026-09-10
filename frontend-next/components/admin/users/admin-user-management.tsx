"use client";

import {ChevronLeft, ChevronRight} from "lucide-react";
import {useState} from "react";

import {AdminUserDetail} from "@/components/admin/users/admin-user-detail";
import {AdminUserFilters} from "@/components/admin/users/admin-user-filters";
import {AdminUserList} from "@/components/admin/users/admin-user-list";
import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminUsers} from "@/hooks/use-admin-users";
import type {AdminUserSearchCondition} from "@/types/admin";

/** 회원 검색과 상세 관리 화면 사이의 전환을 담당합니다. */
export function AdminUserManagement() {
    const [condition, setCondition] = useState<AdminUserSearchCondition>({});
    const [page, setPage] = useState(0);
    const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
    const users = useAdminUsers(condition, page);

    if (selectedUserId !== null) {
        return <AdminUserDetail onBack={() => setSelectedUserId(null)} userId={selectedUserId}/>;
    }

    return (
        <section aria-labelledby="admin-users-tab" className="grid gap-6 p-5 sm:p-8 md:p-10" id="admin-users-panel" role="tabpanel">
            <header>
                <p className="text-xs font-extrabold text-brand">MEMBERS</p>
                <h2 className="mt-1 text-2xl font-black">회원 관리</h2>
                <p className="mt-2 text-sm text-muted-foreground">회원 상태와 권한을 확인하고 보유 티켓을 관리합니다.</p>
            </header>
            <AdminUserFilters onSearch={(next) => {setCondition(next); setPage(0);}}/>
            {users.error && <Alert message={users.error.message} variant="error"/>}
            {users.isLoading || !users.data ? (
                <p className="py-16 text-center text-muted-foreground">회원 목록을 불러오고 있습니다...</p>
            ) : (
                <>
                    <AdminUserList onSelect={setSelectedUserId} users={users.data.content}/>
                    <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
                        <span className="text-muted-foreground">총 {users.data.totalElements.toLocaleString()}명</span>
                        <div className="flex items-center gap-2">
                            <Button aria-label="이전 페이지" disabled={users.data.isFirst} onClick={() => setPage((value) => value - 1)} size="icon-sm" type="button" variant="outline"><ChevronLeft aria-hidden="true"/></Button>
                            <span>{users.data.pageNumber + 1} / {Math.max(users.data.totalPages, 1)}</span>
                            <Button aria-label="다음 페이지" disabled={users.data.isLast} onClick={() => setPage((value) => value + 1)} size="icon-sm" type="button" variant="outline"><ChevronRight aria-hidden="true"/></Button>
                        </div>
                    </div>
                </>
            )}
        </section>
    );
}
