package com.margo.useractivitylogsystem.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID userId,
        Instant timestamp,
        UUID activityId,
        String activityType,
        String details
) {
}
