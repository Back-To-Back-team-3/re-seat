import type {ReactNode} from "react";

import {
    AdminNavigation,
    type AdminTab,
} from "@/components/admin/admin-navigation";

/** 관리자 메뉴와 선택된 업무 화면을 하나의 반응형 작업 영역으로 배치합니다. */
export function AdminShell({
    activeTab,
    onSelect,
    children,
}: {
    activeTab: AdminTab;
    onSelect: (tab: AdminTab) => void;
    children: ReactNode;
}) {
    return (
        <div className="overflow-hidden rounded-modal border border-border bg-surface shadow-sm md:grid md:grid-cols-[220px_minmax(0,1fr)]">
            <AdminNavigation activeTab={activeTab} onSelect={onSelect}/>
            <div className="min-w-0">{children}</div>
        </div>
    );
}
