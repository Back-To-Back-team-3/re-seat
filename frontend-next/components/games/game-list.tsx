"use client";

import {useMemo, useState} from "react";
import {RotateCcw} from "lucide-react";

import {EmptyState} from "@/components/common/empty-state";
import {GameCalendar} from "@/components/games/game-calendar";
import {GAME_STATUS_META, GameCard} from "@/components/games/game-card";
import {Button} from "@/components/ui/button";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {KST_TIME_ZONE} from "@/lib/constants";
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
        return new Intl.DateTimeFormat("en-CA", {
            timeZone: KST_TIME_ZONE,
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
        }).format(new Date());
    });
    const [status, setStatus] = useState<
        GameSummary["bookingStatus"] | "ALL"
    >("ALL");
    const [teamId, setTeamId] = useState<number | "ALL">("ALL");
    const [stadiumId, setStadiumId] = useState<number | "ALL">("ALL");
    const teams = useMemo(() => {
        const entries = new Map<number, string>();
        games.forEach((game) => {
            entries.set(game.homeTeam.teamId, game.homeTeam.name);
            entries.set(game.awayTeam.teamId, game.awayTeam.name);
        });
        return [...entries.entries()].sort((left, right) =>
            left[1].localeCompare(right[1], "ko"),
        );
    }, [games]);
    const stadiums = useMemo(() => {
        const entries = new Map<number, string>();
        games.forEach((game) =>
            entries.set(game.stadium.stadiumId, game.stadium.name),
        );
        return [...entries.entries()].sort((left, right) =>
            left[1].localeCompare(right[1], "ko"),
        );
    }, [games]);
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
        <>
            <label className="grid gap-[5px] text-[11px] font-bold text-muted-foreground">
                구단별
                <Select
                    onValueChange={(value) =>
                        setTeamId(
                            value === "ALL"
                                ? "ALL"
                                : Number(value),
                        )
                    }
                    value={String(teamId)}
                >
                    <SelectTrigger className="min-w-[132px]" size="sm">
                        <SelectValue>
                            {(value) =>
                                value === "ALL"
                                    ? "전체 구단"
                                    : teams.find(([id]) => String(id) === value)?.[1]
                            }
                        </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">전체 구단</SelectItem>
                        {teams.map(([id, name]) => (
                            <SelectItem key={id} value={String(id)}>
                                {name}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </label>
            <label className="grid gap-[5px] text-[11px] font-bold text-muted-foreground">
                구장별
                <Select
                    onValueChange={(value) =>
                        setStadiumId(
                            value === "ALL"
                                ? "ALL"
                                : Number(value),
                        )
                    }
                    value={String(stadiumId)}
                >
                    <SelectTrigger className="min-w-[132px]" size="sm">
                        <SelectValue>
                            {(value) =>
                                value === "ALL"
                                    ? "전체 구장"
                                    : stadiums.find(([id]) => String(id) === value)?.[1]
                            }
                        </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">전체 구장</SelectItem>
                        {stadiums.map(([id, name]) => (
                            <SelectItem key={id} value={String(id)}>
                                {name}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </label>
            <label className="grid gap-[5px] text-[11px] font-bold text-muted-foreground">
                상태
                <Select
                    onValueChange={(value) =>
                        setStatus(
                            value as GameSummary["bookingStatus"] | "ALL",
                        )
                    }
                    value={status}
                >
                    <SelectTrigger className="min-w-[132px]" size="sm">
                        <SelectValue>
                            {(value) =>
                                value === "ALL"
                                    ? "전체 상태"
                                    : GAME_STATUS_META[
                                          value as GameSummary["bookingStatus"]
                                      ]?.label
                            }
                        </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">전체 상태</SelectItem>
                        {Object.entries(GAME_STATUS_META).map(([value, meta]) => (
                            <SelectItem key={value} value={value}>
                                {meta.label}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </label>
        </>
    );

    return (
        <div className="grid gap-6">
            <div
                className="flex flex-wrap items-end justify-between gap-6 max-[640px]:flex-col max-[640px]:items-start">
                <div>
          <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
            — GAME CALENDAR
          </span>
                    <h2 className="mt-[7px] mb-1.5 text-[clamp(32px,3.5vw,46px)] tracking-[-0.04em]">
                        경기 일정
                    </h2>
                    <p className="m-0 text-sm text-muted-foreground">
                        날짜와 구단, 구장을 선택해 전체 예매 상태를 확인하세요.
                    </p>
                </div>
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
