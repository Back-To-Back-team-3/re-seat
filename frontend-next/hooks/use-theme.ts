"use client";

import { useEffect, useState } from "react";

import { getStoredTheme, setStoredTheme, type Theme } from "@/lib/theme";

/**
 * 문서 루트의 data-theme 속성과 localStorage를 하나의 상태로 묶는다.
 *
 * 마운트 시 저장된 값을 읽어 document root에 반영하고(Root Layout의 초기화
 * 스크립트가 이미 같은 값을 칠해 두었을 수 있지만, hydration 이후 React 상태와
 * DOM을 다시 한 번 맞춰 어긋남을 막는다), 토글할 때마다 상태·저장값·DOM 속성을
 * 함께 갱신해 기존 Vite 앱의 즉시 반영 동작을 재현한다.
 */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>("light");

  useEffect(() => {
    const stored = getStoredTheme();
    setTheme(stored);
    document.documentElement.dataset.theme = stored;
  }, []);

  function toggleTheme() {
    const next: Theme = theme === "dark" ? "light" : "dark";
    setTheme(next);
    document.documentElement.dataset.theme = next;
    setStoredTheme(next);
  }

  return { theme, toggleTheme };
}
