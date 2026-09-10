import type {ReactNode} from "react";

import {formatGameDate, formatShortDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";

interface GameSummaryBarProps {
    game: GameSummary;
    children?: ReactNode;
}

/** 예매 흐름에서 선택한 경기의 팀, 일정, 경기장을 요약해 보여준다. */
export function GameSummaryBar({game, children}: GameSummaryBarProps) {
    return (
        <div className="mb-6 flex min-h-[72px] items-center gap-3.5 rounded-[10px] border border-border bg-surface px-[18px] py-3 max-sm:items-start">
            <span className="grid size-[42px] place-items-center rounded-lg bg-foreground font-mono font-black text-surface">
                {formatShortDate(game.gameAt).day}
            </span>
            <div className="grid gap-[3px]">
                <strong className="text-[17px]">
                    {game.homeTeam.name}{" "}
                    <em className="mx-1.5 font-mono text-[9px] not-italic text-brand">
                        VS
                    </em>{" "}
                    {game.awayTeam.name}
                </strong>
                <small className="text-xs text-muted-foreground">
                    {formatGameDate(game.gameAt)} · {game.stadium.name}
                </small>
            </div>
            {children}
        </div>
    );
}
