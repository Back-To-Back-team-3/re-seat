import { formatGameDate, formatShortDate } from "@/lib/date";
import type { GameSummary } from "@/types/game";

/**
 * Vite의 gameStatusMeta(App.tsx)를 그대로 옮긴 상태별 라벨.
 *
 * 히어로의 SELECTED GAME 버튼, 오늘의 경기 패널, 이 카드가 모두 이 맵 하나를
 * 공유하므로 라벨을 여기서만 고치면 세 곳이 함께 Vite와 맞는다.
 */
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
    action: "경기 선택",
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

/**
 * Vite의 .status-pill.* 색상 변형(styles.css). 이 카드와 오늘의 경기 패널이
 * 함께 사용해 상태 색이 화면마다 달라지지 않게 한다.
 */
export const STATUS_PILL_CLASSES: Record<GameSummary["bookingStatus"], string> = {
  SCHEDULED: "bg-[rgba(43,103,203,0.1)] text-[#2b67cb]",
  OPEN: "bg-success/10 text-success",
  CLOSED: "bg-[rgba(91,97,112,0.12)] text-muted-foreground",
  CANCELLED: "bg-brand/10 text-brand",
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
        <span aria-hidden="true" className="absolute inset-x-0 top-0 h-[3px] bg-brand" />
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
            className={`w-fit rounded-full px-[7px] py-[3px] text-[11px] font-black ${STATUS_PILL_CLASSES[game.bookingStatus]}`}
          >
            {meta.label}
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
        disabled={game.bookingStatus !== "OPEN"}
        onClick={() => onBook(game)}
        type="button"
      >
        {selected ? "선택됨" : meta.action}
        <span aria-hidden="true">→</span>
      </button>
    </article>
  );
}
