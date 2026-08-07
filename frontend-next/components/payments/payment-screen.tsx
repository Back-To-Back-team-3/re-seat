"use client";

import { useState } from "react";

import { Alert } from "@/components/common/alert";
import { Countdown } from "@/components/common/countdown";
import { formatPrice } from "@/lib/currency";
import { isDeadlineExpired } from "@/lib/date";
import type { GameSummary } from "@/types/game";
import type { OrderResponse } from "@/types/order";
import type { PaymentResponse } from "@/types/payment";

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-3.5 rounded-control border border-brand bg-brand px-[22px] text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(224,53,53,0.2)] transition-colors hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-[0.48]";

const OUTLINE_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-3.5 rounded-control border border-border bg-surface px-[18px] text-[13px] font-extrabold text-foreground transition-colors hover:border-foreground disabled:cursor-not-allowed disabled:opacity-[0.48]";

type PaymentScreenProps = {
  game: GameSummary | null;
  order: OrderResponse | null;
  payment: PaymentResponse | null;
  busy: boolean;
  error: string | null;
  onOpenPayment: () => void;
  onRefreshOrder: () => void;
  onTickets: () => void;
  onBack: () => void;
};

/**
 * 결제 준비와 승인 완료를 한 화면에서 구분해 보여준다.
 *
 * 승인 여부는 결제 상태와 주문 상태 중 하나만 확정되어도 완료로 판단한다.
 * PG 왕복 중 한쪽 조회가 먼저 갱신될 수 있기 때문이다. 승인 이후에는 결제
 * 요청 버튼과 주문 복귀 버튼을 모두 감춰 이미 끝난 결제를 다시 실행할 수 있는
 * 경로를 남기지 않는다.
 */
export function PaymentScreen({
  game,
  order,
  payment,
  busy,
  error,
  onOpenPayment,
  onRefreshOrder,
  onTickets,
  onBack,
}: PaymentScreenProps) {
  const deadline = order?.paymentDeadline ?? null;

  // 만료를 알려온 기한 값을 그대로 기억한다. 주문이 바뀌어 기한이 달라지면
  // 비교가 자연히 어긋나므로 이전 주문의 만료 상태가 남지 않는다.
  const [expiredDeadline, setExpiredDeadline] = useState<string | null>(null);

  if (error) return <Alert message={error} variant="error" />;
  if (!payment) return <p>결제 정보를 불러오고 있습니다.</p>;

  const approved = payment.status === "APPROVED" || order?.status === "PAID";
  const deadlineExpired =
    (deadline !== null && expiredDeadline === deadline) ||
    isDeadlineExpired(deadline);

  return (
    <section className="grid min-h-[570px] place-items-center">
      <div className="w-[min(660px,100%)] rounded-modal border border-border bg-surface p-12 text-center shadow-card">
        {!approved && (
          <button
            className="mb-6 flex min-h-9 w-fit items-center border-0 bg-transparent p-0 text-[10px] font-extrabold text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-[0.45]"
            disabled={busy}
            onClick={onBack}
            type="button"
          >
            ← 주문으로 돌아가기
          </button>
        )}

        <div
          aria-hidden="true"
          className={`mx-auto mb-[18px] grid size-16 place-items-center rounded-full text-[25px] font-black ${
            approved
              ? "bg-success/10 text-success"
              : "bg-brand/[0.09] text-brand"
          }`}
        >
          {approved ? "✓" : "₩"}
        </div>

        <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
          {approved ? "BOOKING COMPLETE" : "PAYMENT"}
        </span>
        <h1 className="mt-[9px] mb-[7px] text-[42px] tracking-[-0.04em]">
          {approved ? "예매가 완료되었습니다!" : "결제를 완료해주세요."}
        </h1>
        <p className="mb-[26px] text-[11px] text-muted-foreground">
          {approved
            ? "결제와 좌석 확정이 완료되었습니다."
            : "Toss 결제창에서 카드 인증을 마치면 서버가 최종 승인합니다."}
        </p>

        {!approved && deadline && (
          <div
            className={`m-3.5 flex items-center justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-sm font-bold ${
              deadlineExpired
                ? "border-brand/40 bg-brand/12"
                : "border-[color-mix(in_srgb,var(--brand)_28%,var(--border))] bg-brand/[0.07]"
            }`}
          >
            <span>결제 남은 시간</span>
            <Countdown
              onExpire={() => setExpiredDeadline(deadline)}
              target={deadline}
            />
          </div>
        )}

        {!approved && deadlineExpired && (
          <p className="mt-[10px] mr-3.5 mb-3.5 ml-3.5 text-center text-xs font-bold text-brand">
            결제시간이 만료되었습니다. 주문 상태를 확인해주세요.
          </p>
        )}

        <div className="mb-6 grid rounded-[9px] border border-border bg-surface-soft px-5 py-[17px] text-left">
          {[
            ["경기", game?.title ?? "-"],
            ["주문번호", order?.orderNo ?? "-"],
            ["결제번호", payment.paymentNo ?? "-"],
            ["결제 상태", payment.status ?? "준비 전"],
          ].map(([label, value]) => (
            <div
              className="flex items-center justify-between gap-5 border-b border-border py-2"
              key={label}
            >
              <span className="text-xs text-muted-foreground">{label}</span>
              <strong className="max-w-[70%] overflow-hidden font-mono text-xs text-ellipsis whitespace-nowrap">
                {value}
              </strong>
            </div>
          ))}
          <div className="flex items-center justify-between gap-5 py-2">
            <span className="text-xs text-muted-foreground">총 결제금액</span>
            <strong className="max-w-[70%] overflow-hidden font-mono text-xs text-ellipsis whitespace-nowrap">
              {formatPrice(payment.amount ?? order?.totalAmount ?? 0)}
            </strong>
          </div>
        </div>

        {approved ? (
          <button className={PRIMARY_BUTTON} onClick={onTickets} type="button">
            내 티켓 확인 →
          </button>
        ) : (
          <div className="flex justify-center gap-2.5">
            <button
              className={PRIMARY_BUTTON}
              disabled={payment.status !== "READY" || busy || deadlineExpired}
              onClick={onOpenPayment}
              type="button"
            >
              {deadlineExpired ? "결제 시간 만료" : "Toss 결제창 열기 →"}
            </button>
            <button
              className={OUTLINE_BUTTON}
              disabled={!order || busy}
              onClick={onRefreshOrder}
              type="button"
            >
              주문 상태 확인
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
