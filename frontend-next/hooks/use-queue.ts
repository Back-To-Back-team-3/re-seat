"use client";

import {useRouter} from "next/navigation";
import {useEffect, useRef, useState} from "react";

import {cancelQueue, enterQueue, streamQueue} from "@/api/queues";
import {storage} from "@/lib/storage";
import {useBookingStore} from "@/providers/booking-store-provider";
import type {QueueViewState} from "@/types/game";

/**
 * 대기열 등록부터 SSE 입장 허가까지의 연결 생명주기를 관리합니다.
 *
 * 페이지가 처음 열리면 등록 API를 한 번 호출하고 SSE를 연결합니다. 브라우저가
 * 페이지를 벗어나면 AbortController가 fetch와 스트림 reader를 함께 중단해 이전
 * 화면의 이벤트가 새 화면 상태를 바꾸지 못하게 합니다.
 */
export function useQueue(gameId: number) {
    const router = useRouter();
    const setGame = useBookingStore((state) => state.setGame);
    const setQueueExpiry = useBookingStore((state) => state.setQueueExpiry);
    const [queue, setQueue] = useState<QueueViewState | null>(null);
    const [initialRank, setInitialRank] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);
    const controller = useRef<AbortController | null>(null);

    useEffect(() => {
        if (!Number.isFinite(gameId)) return;
        setGame(gameId);
        const abortController = new AbortController();
        controller.current = abortController;

        async function start() {
            try {
                // 1. 서버에 대기열 참가를 등록한 뒤에만 실시간 스트림을 연결한다.
                await enterQueue(gameId, abortController.signal);
                setQueue({
                    gameId,
                    rank: 0,
                    estimatedWaitSeconds: null,
                    queueStatus: "WAITING",
                    admitted: false,
                    registrationPending: true,
                    queueToken: null,
                    tokenExpiresAt: null,
                });

                // 2. rank 이벤트는 화면의 현재 순위를 갱신하고 최초 순위는 진행률 기준으로 보존한다.
                await streamQueue(
                    gameId,
                    {
                        onRank(status) {
                            setInitialRank((current) => current ?? status.rank);
                            setQueue({
                                ...status,
                                gameId,
                                registrationPending: false,
                                queueToken: null,
                                tokenExpiresAt: null,
                            });
                        },
                        onAdmit(event) {
                            // 3. 좌석 API가 요구하는 Queue-Token과 만료 시각을 저장한 뒤 좌석 화면으로 이동한다.
                            storage.local.set("queueToken", event.queueToken);
                            setQueueExpiry(event.tokenExpiresAt);
                            setQueue((current) =>
                                current
                                    ? {
                                        ...current,
                                        admitted: true,
                                        queueStatus: "ADMITTED",
                                        queueToken: event.queueToken,
                                        tokenExpiresAt: event.tokenExpiresAt,
                                    }
                                    : current,
                            );
                            router.push(`/games/${gameId}/seats`);
                        },
                    },
                    abortController.signal,
                );
            } catch (cause) {
                if (!abortController.signal.aborted) {
                    setError(
                        cause instanceof Error
                            ? cause.message
                            : "대기열 연결에 실패했습니다.",
                    );
                }
            }
        }

        void start();
        return () => {
            // 4. unmount cleanup은 등록 이후 어느 단계에 있더라도 같은 연결을 중단한다.
            abortController.abort();
        };
    }, [gameId, router, setGame, setQueueExpiry]);

    return {
        queue,
        initialRank,
        error,
        async cancel() {
            controller.current?.abort();
            await cancelQueue(gameId);
            router.push("/games");
        },
    };
}
