import { formatGameDate } from "@/lib/date";
import type { GameSummary } from "@/types/game";

export const GAME_STATUS_META: Record<
  GameSummary["bookingStatus"],
  { label: string; action: string; description: string }
> = {
  SCHEDULED: {
    label: "예매 예정",
    action: "예매 준비 중",
    description: "예매 오픈 전입니다.",
  },
  OPEN: {
    label: "예매중",
    action: "예매 시작",
    description: "지금 예매할 수 있습니다.",
  },
  CLOSED: {
    label: "예매 종료",
    action: "예매 종료",
    description: "예매가 마감되었습니다.",
  },
  CANCELLED: {
    label: "경기 취소",
    action: "경기 취소",
    description: "취소된 경기입니다.",
  },
};

type GameCardProps = {
  game: GameSummary;
  selected: boolean;
  onSelect: (game: GameSummary) => void;
  onBook: (game: GameSummary) => void;
};

/**
 * 한 경기의 상태와 팀, 경기장 정보를 표시하고 선택과 예매 시작을 구분합니다.
 *
 * 카드 본문은 상세 확인을 위한 선택만 수행하며, 우측 버튼만 대기열 진입으로
 * 이어집니다. OPEN이 아닌 경기는 기존 화면과 동일하게 예매 버튼을 비활성화합니다.
 */
export function GameCard({
  game,
  selected,
  onSelect,
  onBook,
}: GameCardProps) {
  const meta = GAME_STATUS_META[game.bookingStatus];

  return (
    <article
      className={`grid overflow-hidden rounded-panel border bg-surface shadow-card transition ${
        selected ? "border-brand ring-1 ring-brand" : "border-border"
      } md:grid-cols-[1fr_auto]`}
      data-game-id={game.gameId}
    >
      <button
        aria-label={`${game.title} 선택`}
        className="grid cursor-pointer gap-3 border-0 bg-transparent p-5 text-left text-foreground"
        onClick={() => onSelect(game)}
        type="button"
      >
        <span className="w-fit rounded-full bg-muted px-3 py-1 text-xs font-bold text-muted-foreground">
          {meta.label}
        </span>
        <strong className="text-xl">
          {game.homeTeam.name}
          <span className="mx-3 text-sm font-medium text-brand">VS</span>
          {game.awayTeam.name}
        </strong>
        <span className="text-sm text-muted-foreground">
          {formatGameDate(game.gameAt)} · {game.stadium.name} ·{" "}
          {meta.description}
        </span>
      </button>
      <button
        className="cursor-pointer border-0 border-t border-border bg-foreground px-7 py-4 font-bold text-surface disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground md:border-t-0 md:border-l"
        disabled={game.bookingStatus !== "OPEN"}
        onClick={() => onBook(game)}
        type="button"
      >
        {selected && game.bookingStatus === "OPEN" ? "예매 시작" : meta.action}
      </button>
    </article>
  );
}
