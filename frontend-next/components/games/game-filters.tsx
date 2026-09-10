"use client";

import {useMemo} from "react";

import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {GAME_STATUS_META} from "@/lib/game-status";
import type {GameSummary} from "@/types/game";

export type GameFilterId = number | "ALL";
export type GameFilterStatus = GameSummary["bookingStatus"] | "ALL";

type GameFiltersProps = {
    games: GameSummary[];
    teamId: GameFilterId;
    stadiumId: GameFilterId;
    status: GameFilterStatus;
    onTeamChange: (teamId: GameFilterId) => void;
    onStadiumChange: (stadiumId: GameFilterId) => void;
    onStatusChange: (status: GameFilterStatus) => void;
};

/** 경기 목록의 구단·구장·예매 상태 필터를 표시한다. */
export function GameFilters({
    games,
    teamId,
    stadiumId,
    status,
    onTeamChange,
    onStadiumChange,
    onStatusChange,
}: GameFiltersProps) {
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

    return (
        <>
            <label className="grid gap-[5px] text-[11px] font-bold text-muted-foreground">
                구단별
                <Select
                    onValueChange={(value) =>
                        onTeamChange(value === "ALL" ? "ALL" : Number(value))
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
                        onStadiumChange(value === "ALL" ? "ALL" : Number(value))
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
                        onStatusChange(value as GameFilterStatus)
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
}
