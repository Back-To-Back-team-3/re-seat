import { storage } from "@/lib/storage";

export type Theme = "light" | "dark";

const STORAGE_KEY = "theme";

/**
 * 기존 Vite 앱과 같은 localStorage key("theme")를 사용해 사용자가 마지막으로
 * 선택한 테마를 읽는다. 저장된 값이 없거나 예상 밖의 값이면 light로 취급해
 * 잘못된 문자열이 document root에 그대로 반영되지 않게 한다.
 */
export function getStoredTheme(): Theme {
  const stored = storage.local.get(STORAGE_KEY);
  return stored === "dark" ? "dark" : "light";
}

export function setStoredTheme(theme: Theme): void {
  storage.local.set(STORAGE_KEY, theme);
}
