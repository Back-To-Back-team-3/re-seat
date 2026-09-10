"use client";

import {CalendarCog, UsersRound} from "lucide-react";

import {cn} from "@/lib/utils";

export type AdminTab = "users" | "games";

const items = [
    {id: "users" as const, label: "회원 관리", icon: UsersRound},
    {id: "games" as const, label: "경기/좌석 관리", icon: CalendarCog},
];

export function AdminNavigation({
    activeTab,
    onSelect,
}: {
    activeTab: AdminTab;
    onSelect: (tab: AdminTab) => void;
}) {
    return (
        <nav aria-label="관리자 메뉴">
            <div
                className="flex gap-1 border-b border-border p-2 md:min-h-[680px] md:flex-col md:border-r md:border-b-0 md:p-5"
                role="tablist"
            >
                {items.map(({id, label, icon: Icon}) => (
                    <button
                        aria-controls={`admin-${id}-panel`}
                        aria-selected={activeTab === id}
                        className={cn(
                            "flex min-h-11 flex-1 items-center justify-center gap-2 rounded-control px-3 text-sm font-bold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/35 md:flex-none md:justify-start",
                            activeTab === id
                                ? "bg-foreground text-background"
                                : "text-muted-foreground hover:bg-muted hover:text-foreground",
                        )}
                        id={`admin-${id}-tab`}
                        key={id}
                        onClick={() => onSelect(id)}
                        role="tab"
                        type="button"
                    >
                        <Icon aria-hidden="true" className="size-4"/>
                        {label}
                    </button>
                ))}
            </div>
        </nav>
    );
}
