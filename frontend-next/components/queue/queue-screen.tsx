import {Alert} from "@/components/common/alert";
import {formatGameDate, formatShortDate} from "@/lib/date";
import type {GameSummary, QueueViewState} from "@/types/game";

const PRIMARY_BUTTON =
    "inline-flex min-h-11 items-center justify-center gap-3.5 rounded-control border border-brand bg-brand px-[22px] text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(224,53,53,0.2)] transition-colors hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-[0.48]";

const OUTLINE_BUTTON =
    "inline-flex min-h-11 items-center justify-center gap-3.5 rounded-control border border-border bg-surface px-[18px] text-[13px] font-extrabold text-foreground transition-colors hover:border-foreground disabled:cursor-not-allowed disabled:opacity-[0.48]";

const TEXT_BUTTON_DANGER =
    "mt-2.5 inline-flex min-h-9 items-center justify-center gap-3.5 px-2 text-[13px] font-extrabold text-brand disabled:cursor-not-allowed disabled:opacity-[0.48]";

/**
 * 대기열 화면. Vite의 QueueScreen(App.tsx)과 같은 순서로 선택 경기 요약,
 * 대기 카드(제목·순번·진행률·상태), 안내 문구를 보여준다.
 *
 * SSE로 들어오는 순위 갱신과 별개로 "상태 확인" 버튼은 기존 REST 조회
 * 경로(getQueueStatus)를 그대로 재사용하는 수동 새로고침이며, 이 컴포넌트는
 * 그 결과를 받아 표시만 한다.
 */
export function QueueScreen({
                                game,
                                queue,
                                initialRank,
                                error,
                                busy,
                                onRefresh,
                                onCancel,
                                onContinue,
                            }: {
    game?: GameSummary;
    queue: QueueViewState | null;
    initialRank: number | null;
    error: string | null;
    busy: boolean;
    onRefresh: () => void;
    onCancel: () => void;
    onContinue: () => void;
}) {
    const admitted = Boolean(queue?.queueToken);
    const currentRank = queue?.rank ?? 0;
    // Vite App.tsx의 queueProgress 계산과 동일: 최초 순번 대비 남은 순번 비율이며
    // 최소 4%를 유지해 막 등록했을 때도 진행률 바가 완전히 비어 보이지 않게 한다.
    const progress =
        initialRank && currentRank
            ? Math.max(4, Math.min(100, ((initialRank - currentRank) / initialRank) * 100))
            : admitted
                ? 100
                : 4;

    return (
        <section className="mx-auto w-[min(820px,100%)]">
            {error && <Alert message={error} variant="error"/>}

            {game && (
                <div
                    className="mb-6 flex min-h-[72px] items-center gap-3.5 rounded-[10px] border border-border bg-surface px-[18px] py-3 max-sm:items-start">
          <span
              className="grid size-[42px] place-items-center rounded-lg bg-foreground font-mono font-black text-surface">
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
                </div>
            )}

            <div
                className={`rounded-modal border bg-surface px-[62px] pt-12 pb-[38px] text-center shadow-card max-sm:px-5 max-sm:pt-[38px] max-sm:pb-7 ${
                    admitted ? "border-success/35" : "border-border"
                }`}
            >
                <div
                    className={`mx-auto mb-[18px] grid size-[62px] place-items-center rounded-full text-[25px] font-black ${
                        admitted ? "bg-success/10 text-success" : "bg-brand/8 text-brand"
                    }`}
                >
                    {admitted ? (
                        "✓"
                    ) : (
                        <span className="size-6 animate-spin rounded-full border-[3px] border-brand/20 border-t-brand"/>
                    )}
                </div>
                <span className="inline-block font-mono text-[11px] font-extrabold tracking-[2px] text-brand">
          VIRTUAL QUEUE
        </span>
                <h1 className="mt-[10px] mb-2 text-[clamp(34px,5vw,48px)]">
                    {admitted
                        ? "대기가 완료되었습니다."
                        : queue?.registrationPending
                            ? "대기열 등록 중입니다."
                            : "접속 인원이 많아 대기 중입니다."}
                </h1>
                <p className="mb-8 text-xs text-muted-foreground">
                    {admitted
                        ? "입장 토큰이 발급되었습니다. 유효 시간 안에 좌석을 선택해주세요."
                        : "창을 닫지 않으면 순번이 자동으로 갱신됩니다."}
                </p>

                <div className="mx-auto mb-5 grid gap-1.5">
          <span className="text-[11px] text-muted-foreground">
            나의 대기순서
          </span>
                    <strong className="font-mono text-[clamp(42px,7vw,66px)] tracking-[-3px]">
                        {admitted
                            ? "입장 가능"
                            : queue?.registrationPending
                                ? "접수 중"
                                : `${queue?.rank.toLocaleString() ?? "-"}번째`}
                    </strong>
                </div>

                <div className="mb-6 h-[7px] overflow-hidden rounded-[20px] bg-surface-soft">
          <span
              className="block h-full rounded-[inherit] bg-gradient-to-r from-brand to-accent transition-[width] duration-500 [transition-timing-function:ease]"
              style={{width: `${progress}%`}}
          />
                </div>

                <div
                    className="mb-7 grid grid-cols-2 border-t border-b border-border py-5 max-sm:grid-cols-1 max-sm:gap-3">
                    <div
                        className="grid gap-1 border-r border-border max-sm:border-r-0 max-sm:border-b max-sm:pb-3 max-sm:text-left">
                        <span className="text-xs text-muted-foreground">현재 상태</span>
                        <strong className="font-mono text-xs">
                            {queue?.queueStatus ?? "WAITING"}
                        </strong>
                    </div>
                    <div className="grid gap-1 max-sm:text-left">
            <span className="text-xs text-muted-foreground">
              예상 대기시간
            </span>
                        <strong className="font-mono text-xs">
                            {queue?.estimatedWaitSeconds == null
                                ? "계산 중"
                                : `${queue.estimatedWaitSeconds}초`}
                        </strong>
                    </div>
                </div>

                <div className="flex justify-center gap-2.5 max-sm:flex-col">
                    <button
                        className={OUTLINE_BUTTON}
                        disabled={busy}
                        onClick={onRefresh}
                        type="button"
                    >
                        상태 확인
                    </button>
                    <button
                        className={PRIMARY_BUTTON}
                        disabled={!admitted || busy}
                        onClick={onContinue}
                        type="button"
                    >
                        좌석 선택으로 이동 →
                    </button>
                </div>
                {!admitted && (
                    <button
                        className={TEXT_BUTTON_DANGER}
                        disabled={busy}
                        onClick={onCancel}
                        type="button"
                    >
                        예매 취소하고 돌아가기
                    </button>
                )}
            </div>

            <div className="mt-3.5 flex gap-3 rounded-[10px] border border-border bg-surface-soft px-[18px] py-[15px]">
        <span
            className="grid size-[22px] shrink-0 place-items-center rounded-full bg-foreground font-serif text-xs font-black text-surface">
          i
        </span>
                <p className="m-0 text-xs leading-[1.5] text-muted-foreground">
                    <strong className="text-foreground">
                        대기 중에는 페이지를 닫지 마세요.
                    </strong>{" "}
                    실시간 연결이 종료되더라도 상태 확인 버튼으로 현재 순번을 다시 조회할
                    수 있습니다.
                </p>
            </div>
        </section>
    );
}
