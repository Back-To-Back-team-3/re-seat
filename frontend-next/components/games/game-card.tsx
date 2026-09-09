import {formatGameDate, formatShortDate} from "@/lib/date";
import {
    GAME_STATUS_BADGE_CLASSES,
    GAME_STATUS_META,
} from "@/lib/game-status";
import type {GameSummary} from "@/types/game";

type GameCardProps = {
    game: GameSummary;
    completed: boolean;
    selected: boolean;
    onSelect: (game: GameSummary) => void;
};

/**
 * 한 경기의 상태와 팀, 경기장 정보를 표시합니다.
 *
 * 카드 본문과 하단 버튼은 모두 경기만 선택한다. 실제 대기열 진입은 상단의
 * 선택 경기 패널에서 처리해 기존 Vite 화면의 사용자 흐름을 유지한다.
 */
export function GameCard({
                             game,
                             completed,
                             selected,
                             onSelect,
                         }: GameCardProps) {
    const meta = GAME_STATUS_META[game.bookingStatus];
    const date = formatShortDate(game.gameAt);

    return (
        <article
            className={`relative overflow-hidden rounded-panel border bg-surface shadow-card transition ${
                selected
                    ? "border-brand shadow-[0_14px_30px_rgb(21_28_46/8%)]"
                    : "border-border"
            }`}
            data-game-id={game.gameId}
        >
            {selected && (
                <span aria-hidden="true" className="absolute inset-x-0 top-0 h-[3px] bg-brand"/>
            )}
            <button
                aria-label={`${game.title} 선택`}
                className="grid w-full cursor-pointer grid-cols-[52px_1fr] gap-3 border-0 bg-transparent px-4 pt-5 pb-4 text-left text-foreground"
                onClick={() => onSelect(game)}
                type="button"
            >
                <div className="grid content-start justify-items-center border-r border-border pr-3">
                    <span className="text-xs text-muted-foreground">{date.month}</span>
                    <strong className="font-mono text-[27px] leading-[1.1]">
                        {date.day}
                    </strong>
                    <small className="text-xs text-muted-foreground">
                        {date.weekday}
                    </small>
                </div>
                <div className="grid min-w-0 content-start gap-1.5">
          <span
              className={`w-fit rounded-full px-[7px] py-[3px] text-[11px] font-black ${GAME_STATUS_BADGE_CLASSES[game.bookingStatus]}`}
          >
            {completed ? "예매 완료" : meta.label}
          </span>
                    <div className="mt-[3px] grid gap-0.5">
                        <strong className="truncate text-base">
                            {game.homeTeam.name}
                        </strong>
                        <span className="text-xs font-black text-brand">VS</span>
                        <strong className="truncate text-base">
                            {game.awayTeam.name}
                        </strong>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                        {game.title}
                    </p>
                    <small className="text-xs leading-[1.45] text-muted-foreground">
                        {formatGameDate(game.gameAt)} · {game.stadium.name} ·{" "}
                        {meta.description}
                    </small>
                </div>
            </button>
            <button
                className={`flex w-full cursor-pointer items-center justify-between border-0 border-t border-border bg-surface-soft/72 px-4 py-3 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-[0.58] ${
                    selected ? "text-brand" : "text-muted-foreground"
                }`}
                disabled={game.bookingStatus !== "OPEN" || completed}
                onClick={() => onSelect(game)}
                type="button"
            >
                {completed ? "예매 완료" : selected ? "선택됨" : meta.action}
                <span aria-hidden="true">→</span>
            </button>
        </article>
    );
}
