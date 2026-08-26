import {EmptyState} from "@/components/common/empty-state";
import {formatGameDate} from "@/lib/date";
import type {GameSummary} from "@/types/game";
import type {TicketSummary} from "@/types/ticket";

/**
 * Vite의 .status-pill.* 색상 변형(styles.css, "2026 UI readability" 구간의
 * 재정의까지 반영한 최종 값)을 옮긴 티켓 상태 배지 색상.
 *
 * ISSUED/CANCELED만 전용 색이 있고 USED는 재정의 대상이 아니어서 기본(회색)
 * 배지 그대로 남는다.
 */
const STATUS_PILL_CLASSES: Record<TicketSummary["status"], string> = {
    ISSUED: "bg-success/10 text-success",
    USED: "bg-surface-soft text-muted-foreground",
    CANCELED: "bg-brand/10 text-brand",
};

export function TicketList({
                               tickets,
                               games,
                               source,
                               reloading,
                               onReload,
                           }: {
    tickets: TicketSummary[];
    games: GameSummary[];
    source: "api" | "mock";
    reloading: boolean;
    onReload: () => void;
}) {
    return (
        <section className="mx-auto w-full max-w-[1120px]">
            <div
                className="mb-[30px] flex items-end justify-between gap-6 max-[640px]:flex-col max-[640px]:items-start">
                <div>
          <span className="inline-block text-xs font-extrabold tracking-[0.1em] text-brand">
            MY TICKETS
          </span>
                    <h1 className="mt-[7px] mb-1.5 text-[clamp(32px,3.5vw,46px)] tracking-[-0.04em]">
                        내 티켓
                    </h1>
                    <p className="m-0 text-sm text-muted-foreground">
                        결제 완료 후 발급된 모바일 티켓을 확인합니다.
                    </p>
                </div>
                <button
                    className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-3.5 rounded-control border border-border bg-surface px-[18px] text-[13px] font-extrabold text-foreground transition enabled:hover:-translate-y-px enabled:hover:border-foreground disabled:cursor-not-allowed disabled:opacity-[0.48]"
                    disabled={reloading}
                    onClick={onReload}
                    type="button"
                >
                    ↻ 티켓 새로고침
                </button>
            </div>

            {source === "mock" && (
                <div
                    className="my-[18px] flex items-center gap-2.5 rounded-control border border-[rgba(245,166,35,0.35)] bg-[rgba(245,166,35,0.08)] px-[14px] py-3 text-xs text-muted-foreground">
          <span className="rounded-[4px] bg-accent px-1.5 py-[3px] font-mono text-[9px] font-black text-[#201606]">
            MOCK
          </span>
                    결제 완료 데이터를 바탕으로 만든 임시 티켓입니다.
                </div>
            )}

            {tickets.length === 0 ? (
                <EmptyState
                    description="경기 예매와 결제를 완료하면 이곳에 티켓이 표시됩니다."
                    title="보유한 티켓이 없습니다."
                />
            ) : (
                <div className="grid gap-3.5">
                    {tickets.map((ticket) => {
                        const gameTitle =
                            games.find((game) => game.gameId === ticket.gameId)?.title ??
                            `경기 #${ticket.gameId}`;

                        return (
                            <article
                                className="grid grid-cols-[112px_1fr_130px] overflow-hidden rounded-[13px] border border-border bg-surface max-[640px]:grid-cols-[80px_1fr]"
                                key={ticket.ticketId}
                            >
                                <div
                                    className="grid place-items-center content-center gap-[7px] bg-foreground text-surface">
                  <span className="font-brand text-xl font-black">
                    Re:<b className="text-brand">Seat</b>
                  </span>
                                    <small className="font-mono text-xs tracking-[1.5px]">
                                        ADMIT ONE
                                    </small>
                                </div>
                                <div className="grid content-center gap-[5px] p-[22px]">
                  <span
                      className={`w-fit rounded-full px-[9px] py-[5px] text-[11px] font-black ${STATUS_PILL_CLASSES[ticket.status]}`}
                  >
                    {ticket.status}
                  </span>
                                    <h2 className="mt-[3px] mb-0 text-[21px]">{gameTitle}</h2>
                                    <p className="m-0 text-[10px] text-muted-foreground">
                                        {ticket.seat}
                                    </p>
                                    <small className="text-xs text-muted-foreground">
                                        {formatGameDate(ticket.gameAt)}
                                    </small>
                                    <strong className="mt-1 font-mono text-xs">
                                        {ticket.ticketNo}
                                    </strong>
                                </div>
                                <div
                                    className="grid place-items-center content-center gap-2 border-l border-dashed border-border max-[640px]:col-span-2 max-[640px]:min-h-[100px] max-[640px]:border-t max-[640px]:border-l-0">
                  <span
                      className="grid size-[65px] place-items-center border-[7px] border-double border-foreground font-mono text-[11px] font-black">
                    QR
                  </span>
                                    <small
                                        className="max-w-[90px] overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs text-muted-foreground">
                                        {ticket.qrToken}
                                    </small>
                                </div>
                            </article>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
