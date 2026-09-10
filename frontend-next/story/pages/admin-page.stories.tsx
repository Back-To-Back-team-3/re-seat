import type {Meta, StoryObj} from "@storybook/nextjs-vite";
import {useState} from "react";

import type {AdminTab} from "@/components/admin/admin-navigation";
import {AdminShell} from "@/components/admin/admin-shell";
import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import {AdminUserFilters} from "@/components/admin/users/admin-user-filters";
import {AdminUserList} from "@/components/admin/users/admin-user-list";
import {storyGames} from "@/story/fixtures";
import type {AdminUser} from "@/types/admin";

const users = [
    {
        id: 7,
        email: "fan@example.com",
        name: "야구팬",
        nickname: "응원단장",
        phone: "010-1234-5678",
        role: "USER",
        status: "ACTIVE",
        isVerified: true,
        createdAt: "2026-09-01 10:00:00",
        updatedAt: "2026-09-01 10:00:00",
    },
    {
        id: 8,
        email: "admin@example.com",
        name: "운영자",
        nickname: null,
        phone: null,
        role: "ADMIN",
        status: "ACTIVE",
        isVerified: true,
        createdAt: "2026-08-20 09:00:00",
        updatedAt: "2026-09-09 18:00:00",
    },
] satisfies AdminUser[];

const meta = {
    title: "페이지/관리자 페이지",
    parameters: {layout: "fullscreen"},
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

function AdminPagePreview({initialTab = "users"}: {initialTab?: AdminTab}) {
    const [activeTab, setActiveTab] = useState<AdminTab>(initialTab);
    const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
    const selectedGame =
        storyGames.find((game) => game.gameId === selectedGameId) ?? null;

    return (
        <main className="mx-auto min-h-[760px] w-full max-w-[var(--width-shell)] px-[var(--gutter-desktop)] py-12 max-sm:px-[var(--gutter-mobile)]">
            <header className="mb-8">
                <p className="text-xs font-bold text-brand">ADMIN</p>
                <h1 className="mt-1 text-3xl font-black">관리자 페이지</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    회원과 경기 운영 상태를 관리합니다.
                </p>
            </header>
            <AdminShell activeTab={activeTab} onSelect={setActiveTab}>
                {activeTab === "users" ? (
                    <section className="grid gap-6 p-5 sm:p-8 md:p-10">
                        <div>
                            <h2 className="text-2xl font-black">회원 관리</h2>
                            <p className="mt-2 text-sm text-muted-foreground">
                                회원 상태와 권한, 보유 티켓을 관리합니다.
                            </p>
                        </div>
                        <AdminUserFilters onSearch={() => undefined}/>
                        <AdminUserList onSelect={() => undefined} users={users}/>
                    </section>
                ) : (
                    <section className="grid gap-6 p-5 sm:p-8 md:p-10">
                        <div>
                            <h2 className="text-2xl font-black">경기/좌석 관리</h2>
                            <p className="mt-2 text-sm text-muted-foreground">
                                경기 예매 상태와 좌석 재고를 관리합니다.
                            </p>
                        </div>
                        <AdminGameList
                            games={storyGames}
                            onSelect={setSelectedGameId}
                            selectedGameId={selectedGameId}
                        />
                        {selectedGame && (
                            <AdminGameOperations
                                game={selectedGame}
                                isOpeningInventory={false}
                                isUpdatingStatus={false}
                                onOpenInventory={() => undefined}
                                onUpdateStatus={() => undefined}
                            />
                        )}
                    </section>
                )}
            </AdminShell>
        </main>
    );
}

export const Users: Story = {
    render: () => <AdminPagePreview/>,
};

export const Games: Story = {
    render: () => <AdminPagePreview initialTab="games"/>,
};
