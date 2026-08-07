import type { Metadata } from "next";

import { QueryProvider } from "@/providers/query-provider";

import "./globals.css";

export const metadata: Metadata = {
  title: "Re:Seat",
  description: "Re:Seat 야구 예매 서비스",
};

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
 * 인라인 스크립트의 "theme" 키와 dark/light 기본값은 lib/theme.ts의
 * STORAGE_KEY와 getStoredTheme()와 동기화되어야 합니다. 스크립트가
 * hydration 전에 실행되어야 하므로 상수를 임포트할 수 없습니다.
 */

/**
 * 모든 화면이 공유하는 문서 루트입니다.
 *
 * head의 인라인 스크립트는 React가 hydration을 마치기 전에 저장된 테마를
 * document root에 먼저 칠해, 다크 모드 사용자에게 밝은 화면이 잠깐 보이는
 * 깜빡임을 막습니다. localStorage 접근이 막힌 환경(프라이버시 모드 등)에서는
 * light로 안전하게 대체합니다.
 */
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
