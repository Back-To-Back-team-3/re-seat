type BrowserStorage = {
  get(key: string): string | null;
  set(key: string, value: string): void;
  remove(key: string): void;
  getJson<T>(key: string): T | null;
};

type StorageKind = "localStorage" | "sessionStorage";

function createStorage(kind: StorageKind): BrowserStorage {
  /**
   * Next.js는 같은 모듈을 서버에서도 평가할 수 있다. 서버에는 window가 없으므로
   * 각 호출 시점에 환경을 확인하고, 서버에서는 저장소를 사용하지 않는다.
   */
  const getBrowserStorage = () =>
    typeof window === "undefined" ? null : window[kind];

  return {
    get(key) {
      return getBrowserStorage()?.getItem(key) ?? null;
    },
    set(key, value) {
      getBrowserStorage()?.setItem(key, value);
    },
    remove(key) {
      getBrowserStorage()?.removeItem(key);
    },
    getJson<T>(key: string) {
      const value = getBrowserStorage()?.getItem(key);
      if (value == null) return null;

      try {
        return JSON.parse(value) as T;
      } catch {
        // 저장 중 페이지가 닫혔거나 이전 버전 값이 남아 있어도 화면 진입을 막지 않는다.
        return null;
      }
    },
  };
}

export const storage = {
  local: createStorage("localStorage"),
  session: createStorage("sessionStorage"),
};
