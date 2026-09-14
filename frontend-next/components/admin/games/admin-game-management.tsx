"use client";

import {ChevronLeft, ChevronRight, Plus} from "lucide-react";
import {useState} from "react";

import {AdminGameFilters} from "@/components/admin/games/admin-game-filters";
import {AdminGameList} from "@/components/admin/games/admin-game-list";
import {AdminGameOperations} from "@/components/admin/games/admin-game-operations";
import {AdminGameRegisterForm} from "@/components/admin/games/admin-game-register-form";
import {Alert} from "@/components/common/alert";
import {Button} from "@/components/ui/button";
import {useAdminGames} from "@/hooks/use-admin-games";
import type {AdminGameSearchCondition} from "@/types/admin";

const GAMES_PER_PAGE = 10;

/** 경기 검색 조건을 서버에 전달하고 선택한 경기의 운영 작업을 표시합니다. */
export function AdminGameManagement() {
    const [condition, setCondition] = useState<AdminGameSearchCondition>({});
    const [page, setPage] = useState(0);
    const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
    const [showRegisterForm, setShowRegisterForm] = useState(false);
    const adminGames = useAdminGames(condition, page, GAMES_PER_PAGE);
    const selectedGame = adminGames.games.find((game) => game.gameId === selectedGameId) ?? null;

    return (
        <section aria-labelledby="admin-games-tab" className="grid gap-6 p-5 sm:p-8 md:p-10" id="admin-games-panel" role="tabpanel">
            <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div>
                    <p className="text-xs font-extrabold text-brand">OPERATIONS</p>
                    <h2 className="mt-1 text-2xl font-black">경기/좌석 관리</h2>
                    <p className="mt-2 text-sm text-muted-foreground">경기를 조건별로 조회하고 예매 상태와 좌석 재고를 관리합니다.</p>
                    <p className="mt-1 text-xs text-muted-foreground">팀·구장 ID는 아래 경기 목록에서 확인할 수 있습니다.</p>
                </div>
                {!showRegisterForm && <Button onClick={() => setShowRegisterForm(true)} type="button"><Plus aria-hidden="true"/>경기 등록</Button>}
            </header>
            {showRegisterForm && (
                <AdminGameRegisterForm
                    isRegistering={adminGames.isRegistering}
                    onCancel={() => setShowRegisterForm(false)}
                    onRegister={adminGames.registerGame}
                />
            )}
            <AdminGameFilters onSearch={(next) => {
                setCondition(next);
                setPage(0);
                setSelectedGameId(null);
            }}/>
            {adminGames.error && <Alert message={adminGames.error.message} variant="error"/>}
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
            {adminGames.isLoading ? (
                <p className="py-16 text-center text-muted-foreground">경기 목록을 불러오고 있습니다...</p>
            ) : adminGames.page ? (
                <div className="grid gap-4">
                    <AdminGameList games={adminGames.games} onSelect={setSelectedGameId} selectedGameId={selectedGame?.gameId ?? null}/>
                    <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
                        <span className="text-muted-foreground">총 {adminGames.page.totalElements.toLocaleString()}경기</span>
                        <nav aria-label="경기 목록 페이지" className="flex items-center gap-2">
                            <Button aria-label="이전 페이지" disabled={adminGames.page.pageNumber === 0} onClick={() => {setPage((value) => Math.max(0, value - 1)); setSelectedGameId(null);}} size="icon-sm" type="button" variant="outline"><ChevronLeft aria-hidden="true"/></Button>
                            <span>{adminGames.page.pageNumber + 1} / {Math.max(adminGames.page.totalPages, 1)} 페이지</span>
                            <Button aria-label="다음 페이지" disabled={adminGames.page.pageNumber + 1 >= adminGames.page.totalPages} onClick={() => {setPage((value) => Math.min(adminGames.page!.totalPages - 1, value + 1)); setSelectedGameId(null);}} size="icon-sm" type="button" variant="outline"><ChevronRight aria-hidden="true"/></Button>
                        </nav>
                    </div>
                </div>
            ) : null}
        </section>
    );
}
