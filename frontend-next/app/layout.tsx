import type { Metadata } from "next";

import { Footer } from "@/components/layout/footer";
import { Header } from "@/components/layout/header";
import { QueryProvider } from "@/providers/query-provider";

import "./globals.css";

export const metadata: Metadata = {
  title: "Re:Seat",
  description: "Re:Seat 야구 예매 서비스",
};

/**
 * 인라인 스크립트의 "theme" 키와 dark/light 기본값은 lib/theme.ts의
 * STORAGE_KEY와 getStoredTheme()와 동기화되어야 합니다. 스크립트가
 * hydration 전에 실행되어야 하므로 상수를 임포트할 수 없습니다.
 */
const THEME_INIT_SCRIPT = `
  (function () {
    try {
      var stored = window.localStorage.getItem("theme");
      document.documentElement.dataset.theme = stored === "dark" ? "dark" : "light";
    } catch (error) {
      document.documentElement.dataset.theme = "light";
    }
  })();
`;

/**
 * 모든 화면이 공유하는 문서 루트입니다. Header와 Footer는 모든 공개·예매
 * route에 고정으로 나타나는 Vite의 topbar/footer를 재현합니다. 예매 흐름
 * 전용 진행 표시(BookingHeader)는 이 Layout이 아니라 (booking) route
 * group의 하위 Layout이 children 안쪽에 추가로 배치합니다.
 */
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>
        <QueryProvider>
          <Header />
          {children}
          <Footer />
        </QueryProvider>
      </body>
    </html>
  );
}
