import {Badge} from "@/components/ui/badge";
import {formatGameDate} from "@/lib/date";
import {cn} from "@/lib/utils";
import type {GameSummary} from "@/types/game";

export function AdminGameList({
    games,
    selectedGameId,
    onSelect,
}: {
    games: GameSummary[];
    selectedGameId: number | null;
    onSelect: (gameId: number) => void;
}) {
    if (games.length === 0) {
        return <p className="py-16 text-center text-sm text-muted-foreground">조회된 경기가 없습니다.</p>;
    }

    return (
        <div className="overflow-x-auto rounded-control border border-border">
            <table className="w-full min-w-[780px] border-collapse text-left text-sm">
                <thead className="border-b border-border bg-muted/50 text-xs text-muted-foreground">
                    <tr><th className="px-4 py-3">경기</th><th className="px-4 py-3">구장</th><th className="px-4 py-3">경기 일시</th><th className="px-4 py-3">예매 기간</th><th className="px-4 py-3">상태</th></tr>
                </thead>
                <tbody className="divide-y divide-border">
                    {games.map((game) => (
                        <tr className={cn("hover:bg-muted/40", selectedGameId === game.gameId && "bg-primary/5")} key={game.gameId}>
                            <td className="px-4 py-3"><button aria-label={`경기 선택 ${game.title}`} className="font-bold text-primary hover:underline" onClick={() => onSelect(game.gameId)} type="button">{game.title}</button></td>
                            <td className="px-4 py-3">{game.stadium.name}</td>
                            <td className="px-4 py-3">{formatGameDate(game.gameAt)}</td>
                            <td className="px-4 py-3 text-xs text-muted-foreground">{game.bookingOpenAt.slice(0, 16)}<br/>{game.bookingCloseAt.slice(0, 16)}</td>
                            <td className="px-4 py-3"><Badge variant={game.bookingStatus === "OPEN" ? "success" : game.bookingStatus === "CANCELLED" ? "destructive" : "secondary"}>{game.bookingStatus}</Badge></td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
