import { storage } from "@/lib/storage";

export type Theme = "light" | "dark";

const STORAGE_KEY = "theme";

const listeners = new Set<() => void>();

function emitThemeChange() {
  listeners.forEach((listener) => listener());
}

/**
 * 기존 Vite 앱과 같은 localStorage key("theme")를 사용해 사용자가 마지막으로
 * 선택한 테마를 읽는다. 저장된 값이 없거나 예상 밖의 값이면 light로 취급해
 * 잘못된 문자열이 document root에 그대로 반영되지 않게 한다.
 */
export function getStoredTheme(): Theme {
  const stored = storage.local.get(STORAGE_KEY);
  return stored === "dark" ? "dark" : "light";
}

/**
 * 테마를 저장하고 구독자에게 알립니다.
 *
 * 같은 탭에서는 localStorage를 바꿔도 `storage` 이벤트가 발생하지 않으므로,
 * 값을 직접 저장하는 대신 이 함수를 사용해야 useTheme의 상태도 함께 갱신됩니다.
 */
export function setStoredTheme(theme: Theme): void {
  storage.local.set(STORAGE_KEY, theme);
  emitThemeChange();
}

/**
 * 브라우저 테마 저장소의 변경을 React에 전달할 구독을 등록합니다.
 *
 * 현재 탭에서 발생한 변경은 `setStoredTheme()`이 알리고, 다른 탭에서 바뀐
 * localStorage는 브라우저의 `storage` 이벤트가 알립니다. 반환 함수는 컴포넌트가
 * 사라질 때 두 구독을 모두 정리하는 데 사용됩니다.
 *
 * @param listener 테마를 다시 읽어야 할 때 호출할 함수
 * @returns 등록한 구독과 이벤트 리스너를 제거하는 함수
 */
export function subscribeTheme(listener: () => void) {
  listeners.add(listener);
  window.addEventListener("storage", listener);

  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

/**
 * useSyncExternalStore가 값 변경 여부를 비교할 수 있도록 원시 문자열을 반환한다.
 *
 * 원시 문자열은 같은 내용일 때 참조가 바뀌지 않으므로 불필요한 재렌더링을 만들지 않는다.
 */
export function getThemeSnapshot(): Theme {
  return getStoredTheme();
}

/**
 * 서버 렌더링에는 브라우저별 저장값이 없다는 고정 스냅샷을 제공한다.
 *
 * 서버 모듈에서 localStorage를 읽지 않으므로 서로 다른 요청의 테마가 섞이지
 * 않으며, Root Layout의 초기화 스크립트가 칠하는 기본값과도 일치한다.
 */
export function getServerThemeSnapshot(): Theme {
  return "light";
}
