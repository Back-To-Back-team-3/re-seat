"use client";

import {Ticket, UserRound} from "lucide-react";

import {cn} from "@/lib/utils";

export type MyPageTab = "tickets" | "account";

const navigationItems = [
    {id: "tickets" as const, label: "티켓 관리", icon: Ticket},
    {id: "account" as const, label: "회원정보 관리", icon: UserRound},
];

export function MyPageNavigation({
    activeTab,
    onSelect,
}: {
    activeTab: MyPageTab;
    onSelect: (tab: MyPageTab) => void;
}) {
    return (
        <nav aria-label="마이페이지 메뉴">
            <div
                className="flex gap-1 border-b border-border p-2 md:min-h-[560px] md:flex-col md:border-r md:border-b-0 md:p-5"
                role="tablist"
            >
                {navigationItems.map(({id, label, icon: Icon}) => (
                    <button
                        aria-controls={`mypage-${id}-panel`}
                        aria-selected={activeTab === id}
                        className={cn(
                            "flex min-h-11 flex-1 items-center justify-center gap-2 rounded-control px-3 text-sm font-bold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/35 md:flex-none md:justify-start",
                            activeTab === id
                                ? "bg-primary text-primary-foreground"
                                : "text-muted-foreground hover:bg-muted hover:text-foreground",
                        )}
                        id={`mypage-${id}-tab`}
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
