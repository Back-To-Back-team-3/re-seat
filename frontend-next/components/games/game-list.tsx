"use client";

import {useMemo, useState} from "react";
import {RotateCcw} from "lucide-react";

import {EmptyState} from "@/components/common/empty-state";
import {GameCalendar} from "@/components/games/game-calendar";
import {GameCard} from "@/components/games/game-card";
import {
    GameFilters,
    type GameFilterId,
    type GameFilterStatus,
} from "@/components/games/game-filters";
import {Button} from "@/components/ui/button";
import {PageIntro} from "@/components/common/page-intro";
import {getKstDateKey} from "@/lib/date";
import type {GameSummary} from "@/types/game";

type GameListProps = {
    games: GameSummary[];
    completedGameIds: ReadonlySet<number>;
    selectedGameId: number | null;
    onSelect: (game: GameSummary) => void;
    /** games-page.tsx의 gamesQuery.refetch를 그대로 전달받아 재조회 경로를 하나로 유지한다. */
    onReload: () => void;
    reloading: boolean;
};

/**
 * 캘린더 섹션 헤드, 날짜·구단·구장·상태 필터, 시간순 경기 카드를 함께 관리합니다.
 *
 * "선택한 경기" 요약은 이 화면에 두지 않는다. 히어로(games-page.tsx)가 이미
 * SELECTED GAME 패널을 보여주므로 여기서 같은 정보를 다시 렌더링하면 화면에
 * 같은 카드가 두 번 나타난다.
 *
 * 필터 값은 이 화면을 벗어나면 버려지는 UI 상태이므로 전역 store에 올리지 않습니다.
 * 입력 목록이 정렬되지 않았더라도 항상 gameAt과 gameId 순으로 표시해 API 페이지가
 * 나뉘거나 응답 순서가 달라져도 기존 화면 순서를 유지합니다.
 */
export function GameList({
                             games,
                             completedGameIds,
                             selectedGameId,
                             onSelect,
                             onReload,
                             reloading,
                         }: GameListProps) {
    const [selectedDate, setSelectedDate] = useState<string | null>(() => {
        /*
         * 백엔드의 경기 일시는 KST를 기준으로 저장됩니다. 브라우저가 다른 시간대에
         * 있어도 기존 화면과 같은 날짜가 선택되도록 로컬 시간이 아닌 KST 날짜를
         * 초기값으로 사용합니다.
         */
        return getKstDateKey();
    });
    const [status, setStatus] = useState<GameFilterStatus>("ALL");
    const [teamId, setTeamId] = useState<GameFilterId>("ALL");
    const [stadiumId, setStadiumId] = useState<GameFilterId>("ALL");
    const calendarGames = useMemo(
        () =>
            games.filter(
                (game) =>
                    (status === "ALL" || game.bookingStatus === status) &&
                    (teamId === "ALL" ||
                        game.homeTeam.teamId === teamId ||
                        game.awayTeam.teamId === teamId) &&
                    (stadiumId === "ALL" || game.stadium.stadiumId === stadiumId),
            ),
        [games, stadiumId, status, teamId],
    );
    const filteredGames = useMemo(
        () =>
            calendarGames
                .filter(
                    (game) => !selectedDate || game.gameAt.startsWith(selectedDate),
                )
                .slice()
                .sort(
                    (left, right) =>
                        left.gameAt.localeCompare(right.gameAt) ||
                        left.gameId - right.gameId,
                ),
        [calendarGames, selectedDate],
    );

    const filters = (
        <GameFilters
            games={games}
            onStadiumChange={setStadiumId}
            onStatusChange={setStatus}
            onTeamChange={setTeamId}
            stadiumId={stadiumId}
            status={status}
            teamId={teamId}
        />
    );

    return (
        <div className="grid gap-6">
            <div
                className="flex flex-wrap items-end justify-between gap-6 max-[640px]:flex-col max-[640px]:items-start">
                <PageIntro
                    description="날짜와 구단, 구장을 선택해 전체 예매 상태를 확인하세요."
                    eyebrow="— GAME CALENDAR"
                    headingLevel={2}
                    title="경기 일정"
                />
                <Button
                    disabled={reloading}
                    loading={reloading}
                    onClick={onReload}
                    variant="outline"
                >
                    {!reloading && <RotateCcw aria-hidden="true"/>}
                    일정 새로고침
                </Button>
            </div>

            <GameCalendar
                filters={filters}
                games={calendarGames}
                onSelectDate={setSelectedDate}
                selectedDate={selectedDate}
            />

            <div className="flex items-baseline justify-between gap-4">
                <div className="flex items-baseline gap-2.5">
                    <strong className="text-xl">
                        {selectedDate
                            ? `${selectedDate.replaceAll("-", ".")} 경기`
                            : "전체 경기"}
                    </strong>
                    <span className="text-xs text-muted-foreground">
            {filteredGames.length}개 일정
          </span>
                </div>
                {selectedDate && (
                    <Button
                        className="text-muted-foreground"
                        onClick={() => setSelectedDate(null)}
                        size="sm"
                        variant="ghost"
                    >
                        날짜 선택 해제
                    </Button>
                )}
            </div>

            {filteredGames.length === 0 ? (
                <EmptyState
                    description="날짜 또는 상태를 변경해 다른 경기를 확인해주세요."
                    title="조건에 맞는 경기가 없습니다."
                />
            ) : (
                <div className="grid grid-cols-3 gap-3 max-[1024px]:grid-cols-2 max-[640px]:grid-cols-1">
                    {filteredGames.map((game) => (
                        <GameCard
                            completed={completedGameIds.has(game.gameId)}
                            game={game}
                            key={game.gameId}
                            onSelect={onSelect}
                            selected={selectedGameId === game.gameId}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}
