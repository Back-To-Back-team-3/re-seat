import {GAME_STATUS_META, STATUS_PILL_CLASSES,} from "@/components/games/game-card";
import {KST_TIME_ZONE} from "@/lib/constants";
import {formatGameDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";

type TodayGamesPanelProps = {
    games: GameSummary[];
    selectedGameId: number | null;
    onSelect: (game: GameSummary) => void;
};

/**
 * 히어로 우측에서 오늘(KST) 경기만 추려 보여주고, 카드를 누르면 히어로의
 * SELECTED GAME을 바꾼다.
 *
 * 목록 전체를 필터링·정렬하는 GameList와는 책임이 다른, 오늘 하루짜리 요약
 * 패널이라 별도 컴포넌트로 분리했다. "오늘"의 기준은 games-page.tsx가
 * 넘겨주는 games 배열(이미 오늘 것만 필터링됨)을 그대로 신뢰하고, 이 컴포넌트는
 * 날짜 계산을 다시 하지 않는다.
 */
export function TodayGamesPanel({
                                    games,
                                    selectedGameId,
                                    onSelect,
                                }: TodayGamesPanelProps) {
    const heading = new Intl.DateTimeFormat("ko-KR", {
        timeZone: KST_TIME_ZONE,
        year: "numeric",
        month: "long",
        day: "numeric",
    }).format(new Date());

    return (
        <section
            className="relative z-[2] overflow-hidden rounded-[18px] border border-border bg-surface/94 shadow-card backdrop-blur-[18px]">
            <div
                className="flex items-center justify-between gap-4 border-b border-border px-5 py-[17px] max-sm:flex-col max-sm:items-start max-sm:gap-[3px]">
        <span className="text-[13px] font-extrabold text-brand">
          — 오늘의 경기
        </span>
                <strong className="text-xs font-semibold text-muted-foreground">
                    {heading}
                </strong>
            </div>
            {games.length === 0 ? (
                <p className="m-0 px-5 py-[34px] text-center text-[15px] text-muted-foreground">
                    오늘 예정된 경기가 없습니다.
                </p>
            ) : (
                <div className="grid grid-cols-2 max-sm:max-h-[390px] max-sm:grid-cols-1 max-sm:overflow-y-auto">
                    {games.map((game) => (
                        <button
                            className={`grid w-full min-w-0 cursor-pointer gap-[6px] border-0 border-r border-b border-border bg-transparent px-4 py-3 text-left text-foreground [&:nth-child(2n)]:border-r-0 [&:nth-last-child(-n+2)]:border-b-0 only:border-r-0 hover:bg-[color-mix(in_srgb,var(--brand)_6%,var(--surface))] max-sm:border-r-0 max-sm:border-b max-sm:last:border-b-0 ${
                                selectedGameId === game.gameId
                                    ? "bg-[color-mix(in_srgb,var(--brand)_6%,var(--surface))]"
                                    : ""
                            }`}
                            key={game.gameId}
                            onClick={() => onSelect(game)}
                            type="button"
                        >
              <span
                  className={`w-fit justify-self-start rounded-full px-[9px] py-[5px] text-[11px] font-black ${STATUS_PILL_CLASSES[game.bookingStatus]}`}
              >
                {GAME_STATUS_META[game.bookingStatus].label}
              </span>
                            <strong className="truncate text-sm">
                                {game.homeTeam.name}
                                <em className="mx-[5px] text-[11px] text-brand not-italic">
                                    VS
                                </em>
                                {game.awayTeam.name}
                            </strong>
                            <small className="truncate text-[11px] text-muted-foreground">
                                {formatGameDate(game.gameAt)} · {game.stadium.name}
                            </small>
                        </button>
                    ))}
                </div>
            )}
        </section>
    );
}
