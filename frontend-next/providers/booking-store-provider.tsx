"use client";

import {createContext, type ReactNode, useContext, useEffect, useState,} from "react";
import {useStore} from "zustand";

import {
    clearBookingProgress,
    loadBookingProgress,
    saveBookingProgress,
    selectBookingProgress,
} from "@/lib/booking-progress";
import {type BookingState, type BookingStore, createBookingStore,} from "@/stores/booking-store";

const BookingStoreContext = createContext<BookingStore | null>(null);

/**
 * 하나의 예매 라우트 영역에서 공유할 Zustand store 인스턴스를 제공한다.
 *
 * useState의 초기화 함수는 Provider가 처음 만들어질 때 한 번만 실행되므로
 * 리렌더링이 발생해도 선택 좌석과 예약 진행 상태가 유지된다.
 */
export function BookingStoreProvider({children}: { children: ReactNode }) {
    const [store] = useState(() => createBookingStore());

    useEffect(() => {
        // 브라우저가 마운트된 뒤에만 sessionStorage를 읽어 서버 렌더링과 충돌하지 않게 한다.
        store.getState().hydrate(loadBookingProgress());

        return store.subscribe((state) => {
            if (!state.hydrated) return;

            const progress = selectBookingProgress(state);
            if (progress.selectedGameId === null) {
                clearBookingProgress();
                return;
            }
            saveBookingProgress(progress);
        });
    }, [store]);

    return (
        <BookingStoreContext.Provider value={store}>
            {children}
        </BookingStoreContext.Provider>
    );
}

/**
 * 예매 store에서 컴포넌트에 필요한 값만 선택해 구독한다.
 *
 * BookingStoreProvider 밖에서 호출하면 상태 소유 범위를 잘못 사용한 것이므로
 * 조용히 새 store를 만들지 않고 즉시 오류를 발생시킨다.
 */
export function useBookingStore<T>(selector: (state: BookingState) => T) {
    const store = useContext(BookingStoreContext);

    if (!store) {
        throw new Error(
            "useBookingStore는 BookingStoreProvider 안에서 사용해야 합니다.",
        );
    }

    return useStore(store, selector);
}
