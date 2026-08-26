"use client";

import Link from "next/link";
import {useState} from "react";

import {Countdown} from "@/components/common/countdown";
import {OrderSummary} from "@/components/orders/order-summary";
import {formatPrice} from "@/lib/currency";
import type {GameSeat, GameSummary} from "@/types/game";
import type {OrderResponse} from "@/types/order";
import type {ReservationResponse} from "@/types/reservation";

const PRIMARY_BUTTON_FULL =
    "mb-2 inline-flex min-h-11 w-full items-center justify-center gap-3.5 rounded-control border border-brand bg-brand px-[22px] text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(224,53,53,0.2)] transition-colors hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-[0.48]";

const OUTLINE_BUTTON_FULL =
    "mb-2 inline-flex min-h-11 w-full items-center justify-center gap-3.5 rounded-control border border-border bg-surface px-[18px] text-[13px] font-extrabold text-foreground transition-colors hover:border-foreground disabled:cursor-not-allowed disabled:opacity-[0.48]";

const TEXT_BUTTON_DANGER =
    "inline-flex min-h-9 items-center justify-center gap-3.5 border-0 bg-transparent px-2 text-[13px] font-extrabold text-brand disabled:cursor-not-allowed disabled:opacity-[0.48]";

type CheckoutScreenBaseProps = {
    game: GameSummary | null;
    seats: GameSeat[];
    busy: boolean;
    onBack: () => void;
};

type CheckoutScreenProps = CheckoutScreenBaseProps &
    (
        | {
        order: null;
        reservation: ReservationResponse | null;
        onCreateOrder: () => void;
    }
        | {
        order: OrderResponse;
        onRefreshOrder: () => void;
        onCancelOrder: () => void;
        onPayment: () => void;
    }
        );

/**
 * 예약 단계(/checkout)와 주문 생성 이후(/orders/[orderId])가 같은 화면으로
 * 보이도록 만든 공유 프레젠테이션 컴포넌트다. Vite CheckoutScreen처럼 order
 * 유무로 우측 결제 요약의 문구·액션만 갈리고 나머지 레이아웃은 동일하다.
 */
export function CheckoutScreen(props: CheckoutScreenProps) {
    const [deadlineExpired, setDeadlineExpired] = useState(false);
    const {game, seats, busy, onBack} = props;

    if (!props.order && !props.reservation) {
        return (
            <section className="text-center">
                <h1 className="text-2xl font-bold">진행 중인 예약이 없습니다.</h1>
                <Link className="mt-4 inline-block text-brand" href="/games">
                    경기 목록으로 돌아가기
                </Link>
            </section>
        );
    }

    if (!game) {
        return <p>경기 정보를 불러오고 있습니다.</p>;
    }

    const order = props.order;
    const reservation = props.order ? null : props.reservation;
    const amount =
        order?.totalAmount ?? seats.reduce((sum, seat) => sum + seat.price, 0);
    const deadlineTarget = order?.paymentDeadline ?? reservation?.holdExpiresAt ?? null;

    return (
        <section className="mx-auto w-[min(1120px,100%)]">
            <div className="mb-7">
                <button
                    className="mb-[18px] flex min-h-9 w-fit items-center border-0 bg-transparent p-0 text-[10px] font-extrabold text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-[0.45]"
                    disabled={busy}
                    onClick={onBack}
                    type="button"
                >
                    ← 좌석 선택으로
                </button>
                <span className="text-xs font-extrabold tracking-[0.1em] text-brand">
          CHECKOUT
        </span>
                <h1 className="mt-[7px] mb-[6px] text-[clamp(32px,3.5vw,46px)] tracking-[-0.04em]">
                    예매 정보 확인
                </h1>
                <p className="m-0 text-sm text-muted-foreground">
                    선택한 경기와 좌석을 확인한 뒤 주문을 생성해주세요.
                </p>
            </div>

            <div
                className="grid grid-cols-[minmax(0,1.45fr)_minmax(300px,0.75fr)] items-start gap-4 max-[900px]:grid-cols-1">
                <OrderSummary game={game} seats={seats}/>

                <aside
                    className="sticky top-[100px] overflow-hidden rounded-panel border border-border bg-surface p-5 max-[900px]:static">
                    <h2 className="mb-[18px] text-[15px]">결제 금액</h2>
                    <dl className="m-0 grid grid-cols-[1fr_auto] gap-2.5 text-xs text-muted-foreground">
                        <dt>좌석 금액</dt>
                        <dd className="m-0 font-mono text-foreground">
                            {formatPrice(amount)}
                        </dd>
                        <dt>예매 수수료</dt>
                        <dd className="m-0 font-mono text-foreground">0원</dd>
                    </dl>
                    <div className="mt-5 flex items-center justify-between gap-4 border-t border-border py-5">
                        <span className="text-xs text-muted-foreground">최종 결제금액</span>
                        <strong className="font-mono text-[17px]">
                            {formatPrice(amount)}
                        </strong>
                    </div>
                    {deadlineTarget && (
                        <div
                            className={`my-3.5 flex items-center justify-between gap-4 rounded-[10px] border px-4 py-3.5 text-sm font-bold ${
                                deadlineExpired
                                    ? "border-brand/40 bg-brand/12"
                                    : "border-[color-mix(in_srgb,var(--brand)_28%,var(--border))] bg-brand/[0.07]"
                            }`}
                        >
                            <span>{order ? "결제 남은 시간" : "선점 남은 시간"}</span>
                            <Countdown
                                onExpire={() => setDeadlineExpired(true)}
                                target={deadlineTarget}
                            />
                        </div>
                    )}
                    {deadlineExpired && (
                        <p className="mt-[10px] mb-[14px] mx-[14px] text-xs font-bold text-brand">
                            제한시간이 만료되어 더 이상 진행할 수 없습니다.
                        </p>
                    )}
                    {!order ? (
                        <button
                            className={PRIMARY_BUTTON_FULL}
                            disabled={!reservation || busy || deadlineExpired}
                            onClick={props.onCreateOrder}
                            type="button"
                        >
                            주문 생성하기 →
                        </button>
                    ) : (
                        <>
                            <div
                                className="mb-3 grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 rounded-[7px] bg-surface-soft p-3">
                                <span className="text-xs text-muted-foreground">주문번호</span>
                                <strong
                                    className="col-start-1 row-start-2 overflow-hidden font-mono text-xs text-ellipsis">
                                    {order.orderNo}
                                </strong>
                                <small
                                    className="col-start-2 row-span-2 row-start-1 self-center text-xs font-black text-brand">
                                    {order.status}
                                </small>
                            </div>
                            <button
                                className={PRIMARY_BUTTON_FULL}
                                disabled={order.status !== "CREATED" || busy || deadlineExpired}
                                onClick={props.onPayment}
                                type="button"
                            >
                                {deadlineExpired
                                    ? "결제 시간 만료"
                                    : `${formatPrice(order.totalAmount)} 결제 준비 →`}
                            </button>
                            <button
                                className={OUTLINE_BUTTON_FULL}
                                disabled={busy}
                                onClick={props.onRefreshOrder}
                                type="button"
                            >
                                주문 상태 확인
                            </button>
                            <button
                                className={TEXT_BUTTON_DANGER}
                                disabled={order.status !== "CREATED" || busy}
                                onClick={props.onCancelOrder}
                                type="button"
                            >
                                주문 취소
                            </button>
                        </>
                    )}
                    <p className="mt-[9px] text-center text-xs text-muted-foreground">
                        결제 버튼 클릭 시 이용약관과 취소 정책에 동의합니다.
                    </p>
                </aside>
            </div>
        </section>
    );
}
