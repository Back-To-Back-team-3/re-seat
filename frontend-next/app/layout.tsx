import type { Metadata } from "next";

import { QueryProvider } from "@/providers/query-provider";

import "./globals.css";

export const metadata: Metadata = {
  title: "Re:Seat",
  description: "Re:Seat 야구 예매 서비스",
};

/**
 * 모든 화면이 공유하는 문서 루트입니다.
 * 도메인 Provider는 실제 사용 시점에 추가해 초기 셸의 책임을 작게 유지합니다.
 */
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
