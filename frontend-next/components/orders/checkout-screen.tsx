"use client";

import Link from "next/link";
import { useState } from "react";

import { Countdown } from "@/components/common/countdown";
import { SeatSummary } from "@/components/seats/seat-summary";
import type { GameSeat } from "@/types/game";
import type { ReservationResponse } from "@/types/reservation";

export function CheckoutScreen({
  reservation,
  seats,
  busy,
  onCreate,
  onCancel,
}: {
  reservation: ReservationResponse | null;
  seats: GameSeat[];
  busy: boolean;
  onCreate: () => void;
  onCancel: () => void;
}) {
  const [expired, setExpired] = useState(false);

  if (!reservation) {
    return (
      <section className="text-center">
        <h1 className="text-2xl font-bold">진행 중인 예약이 없습니다.</h1>
        <Link className="mt-4 inline-block text-brand" href="/games">
          경기 목록으로 돌아가기
        </Link>
      </section>
    );
  }
  return (
    <section className="grid gap-6">
      <h1 className="text-3xl font-black">예매 정보 확인</h1>
      <p>
        주문 가능 시간{" "}
        <Countdown
          onExpire={() => setExpired(true)}
          target={reservation.holdExpiresAt}
        />
      </p>
      <SeatSummary
        busy={busy}
        locked
        onReserve={() => undefined}
        seats={seats}
      />
      <button
        className="rounded-control bg-brand px-5 py-3 font-bold text-white disabled:bg-muted"
        disabled={busy || expired}
        onClick={onCreate}
        type="button"
      >
        모바일 티켓 주문 생성
      </button>
      <button
        className="text-sm text-muted-foreground"
        disabled={busy}
        onClick={onCancel}
        type="button"
      >
        예약 취소
      </button>
    </section>
  );
}
