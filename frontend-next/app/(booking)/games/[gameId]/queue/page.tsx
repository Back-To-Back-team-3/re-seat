"use client";

import { useParams } from "next/navigation";

import { QueueScreen } from "@/components/queue/queue-screen";
import { useQueue } from "@/hooks/use-queue";

export default function QueuePage() {
  const params = useParams<{ gameId: string }>();
  const queue = useQueue(Number(params.gameId));

  return (
    <QueueScreen
      error={queue.error}
      initialRank={queue.initialRank}
      onCancel={() => {
        void queue.cancel();
      }}
      queue={queue.queue}
    />
  );
}
