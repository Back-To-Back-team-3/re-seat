"use client";

import {useMemo, useState} from "react";

import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import {Alert} from "@/components/common/alert";
import {useAdminGames} from "@/hooks/use-admin-games";

/** 경기 검색, 선택, 운영 작업을 한 화면에서 연결합니다. */
export function AdminGameManagement() {
    const adminGames = useAdminGames();
    const [query, setQuery] = useState("");
    const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
    const filteredGames = useMemo(() => {
        const normalized = query.trim().toLowerCase();
        if (!normalized) return adminGames.games;
        return adminGames.games.filter((game) => `${game.title} ${game.stadium.name}`.toLowerCase().includes(normalized));
    }, [adminGames.games, query]);
    const selectedGame =
        adminGames.games.find((game) => game.gameId === selectedGameId) ?? null;

    return (
        <section aria-labelledby="admin-games-tab" className="grid gap-6 p-5 sm:p-8 md:p-10" id="admin-games-panel" role="tabpanel">
            <header>
                <p className="text-xs font-extrabold text-brand">OPERATIONS</p>
                <h2 className="mt-1 text-2xl font-black">경기/좌석 관리</h2>
                <p className="mt-2 text-sm text-muted-foreground">경기 예매 상태를 전환하고 좌석 재고 생성을 요청합니다.</p>
            </header>
            <label className="grid max-w-xl gap-1 text-xs font-bold">경기 또는 구장 검색
                <input className="h-10 rounded-control border border-border bg-background px-3 text-sm outline-none focus:border-primary" onChange={(event) => setQuery(event.target.value)} placeholder="팀 또는 구장 이름" value={query}/>
            </label>
            {adminGames.error && <Alert message={adminGames.error.message} variant="error"/>}
            {adminGames.isLoading ? (
                <p className="py-16 text-center text-muted-foreground">
                    경기 목록을 불러오고 있습니다...
                </p>
            ) : (
                <AdminGameList
                    games={filteredGames}
                    onSelect={setSelectedGameId}
                    selectedGameId={selectedGame?.gameId ?? null}
                />
            )}
            {selectedGame && (
                <AdminGameOperations
                    game={selectedGame}
                    isOpeningInventory={adminGames.isOpeningInventory}
                    isUpdatingStatus={adminGames.isUpdatingStatus}
                    key={selectedGame.gameId}
                    onOpenInventory={() => adminGames.openInventory(selectedGame.gameId)}
                    onUpdateStatus={(status, reason) => adminGames.updateStatus(selectedGame.gameId, status, reason)}
                />
            )}
        </section>
    );
}
