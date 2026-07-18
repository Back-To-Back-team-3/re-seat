package com.backtoback.reseat.domain.queue.dto.event;

import java.time.Instant;
import java.util.UUID;

public record QueueEntryRequestedEvent(
        UUID eventId,
        Long gameId,
        Long userId,
        Instant requestedAt
) {
}
