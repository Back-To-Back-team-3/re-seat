"use client";

import {useQuery} from "@tanstack/react-query";
import {useParams, useRouter} from "next/navigation";
import {useState} from "react";

import {getGame} from "@/api/games";
import {getQueueStatus} from "@/api/queues";
import {gameKeys} from "@/api/query-keys/games";
import {QueueScreen} from "@/components/queue/queue-screen";
import {useQueue} from "@/hooks/use-queue";
import type {QueueStatusResponse, QueueViewState} from "@/types/game";

export default function QueuePage() {
    const params = useParams<{ gameId: string }>();
    const router = useRouter();
    const gameId = Number(params.gameId);
    const queue = useQueue(gameId);
    const gameQuery = useQuery({
        queryKey: gameKeys.detail(gameId),
        queryFn: () => getGame(gameId),
        enabled: Number.isFinite(gameId),
    });

    const [manualStatus, setManualStatus] = useState<QueueStatusResponse | null>(
        null,
    );
    const [refreshing, setRefreshing] = useState(false);

    // SSE가 새 순번을 반영하면(queue.queue 참조가 바뀜) 수동 새로고침 결과는
    // 더 이상 최신이 아니다. effect 대신 렌더 중 비교해 정리하면 한 프레임 뒤에
    // set-state-in-effect 없이 즉시 다음 렌더에 반영된다.
    const [queueForManualStatus, setQueueForManualStatus] =
        useState<QueueViewState | null>(queue.queue);
    if (queue.queue !== queueForManualStatus) {
        setQueueForManualStatus(queue.queue);
        setManualStatus(null);
    }

    async function handleRefresh() {
        setRefreshing(true);
        try {
            // Vite의 handleRefreshQueue(App.tsx)와 동일하게 기존 상태 조회 API를
            // 그대로 재사용하는 수동 새로고침이며, SSE 연결과는 별개의 경로다.
            setManualStatus(await getQueueStatus(gameId));
        } finally {
            setRefreshing(false);
        }
    }

    const displayQueue =
        manualStatus && queue.queue
            ? {...queue.queue, ...manualStatus, registrationPending: false}
            : queue.queue;

    return (
        <QueueScreen
            busy={refreshing}
            error={queue.error}
            game={gameQuery.data}
            initialRank={queue.initialRank}
            onCancel={() => {
                void queue.cancel();
            }}
            onContinue={() => router.push(`/games/${gameId}/seats`)}
            onRefresh={() => {
                void handleRefresh();
            }}
            queue={displayQueue}
        />
    );
}
