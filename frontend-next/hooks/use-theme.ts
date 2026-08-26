"use client";

import {useEffect, useSyncExternalStore} from "react";

import {getServerThemeSnapshot, getThemeSnapshot, setStoredTheme, subscribeTheme, type Theme,} from "@/lib/theme";

/**
 * 문서 루트의 data-theme 속성과 localStorage를 하나의 상태로 묶는다.
 *
 * React가 `lib/theme.ts`의 외부 저장소를 `useSyncExternalStore`로 구독하므로
 * 마운트 시 별도의 setState 없이도 저장된 값이 초기 상태로 반영된다. 서버
 * 렌더링에서는 고정된 스냅샷("light")을 사용하는데, 이는 Root Layout의
 * 초기화 스크립트가 hydration 전에 칠하는 기본값과 같다. document root의
 * data-theme 속성은 상태가 바뀔 때마다 effect가 동기화해, 토글은 물론 다른
 * 탭에서의 변경도 즉시 화면에 반영되도록 기존 Vite 앱의 동작을 재현한다.
 */
export function useTheme() {
    const theme = useSyncExternalStore(
        subscribeTheme,
        getThemeSnapshot,
        getServerThemeSnapshot,
    );

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
    }, [theme]);

    function toggleTheme() {
        const next: Theme = theme === "dark" ? "light" : "dark";
        setStoredTheme(next);
    }

    return {theme, toggleTheme};
}
