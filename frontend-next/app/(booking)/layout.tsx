import type { ReactNode } from "react";

import { BookingHeader } from "@/components/booking/booking-header";
import { BookingStoreProvider } from "@/providers/booking-store-provider";

/**
 * 경기 선택부터 결제까지 이어지는 예매 화면이 같은 임시 상태를 공유하게 한다.
 *
 * 사용자가 예매 영역을 벗어나면 이 Layout도 제거되므로 선택 좌석과 진행 상태가
 * 함께 폐기된다. 새로고침 복구를 추가하지 않는 기존 사용자 흐름도 그대로 유지한다.
 */
export default function BookingLayout({ children }: { children: ReactNode }) {
  return (
    <BookingStoreProvider>
      <div className="overflow-hidden">
        <BookingHeader />
        <main className="relative mx-auto min-h-[650px] w-full max-w-[var(--width-shell)] px-[var(--gutter-desktop)] pt-12 pb-[90px] max-sm:px-[var(--gutter-mobile)] max-sm:pt-[46px] max-sm:pb-[70px]">
          {children}
        </main>
      </div>
    </BookingStoreProvider>
  );
}
