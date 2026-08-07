import { Alert } from "@/components/common/alert";
import type { QueueViewState } from "@/types/game";

export function QueueScreen({
  queue,
  initialRank,
  error,
  onCancel,
}: {
  queue: QueueViewState | null;
  initialRank: number | null;
  error: string | null;
  onCancel: () => void;
}) {
  const progress =
    queue && initialRank
      ? Math.max(0, Math.min(100, ((initialRank - queue.rank) / initialRank) * 100))
      : 0;

  return (
    <section className="mx-auto grid max-w-2xl gap-6 text-center">
      {error && <Alert message={error} variant="error" />}
      <span className="text-sm font-bold text-brand">BOOKING QUEUE</span>
      <h1 className="text-3xl font-black">예매 대기열에 접속했습니다.</h1>
      <div className="rounded-panel border border-border bg-surface p-8 shadow-card">
        <span className="text-sm text-muted-foreground">현재 대기 순서</span>
        <strong className="mt-2 block text-6xl">
          {queue?.registrationPending ? "등록 중" : `${queue?.rank ?? "-"}번째`}
        </strong>
        <div className="mt-6 h-2 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full bg-brand transition-[width]"
            style={{ width: `${progress}%` }}
          />
        </div>
        <p className="mt-4 text-sm text-muted-foreground">
          예상 대기 시간:{" "}
          {queue?.estimatedWaitSeconds == null
            ? "-"
            : `${queue.estimatedWaitSeconds}초`}
        </p>
      </div>
      <button
        className="justify-self-center rounded-control border border-border bg-background px-5 py-3"
        onClick={onCancel}
        type="button"
      >
        대기열 나가기
      </button>
    </section>
  );
}
