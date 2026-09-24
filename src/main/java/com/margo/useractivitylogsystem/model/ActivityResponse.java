package com.margo.useractivitylogsystem.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ActivityResponse(
        UUID userId,
        Instant activityTimestamp,
        UUID activityId,
        String activityType,
        String details
) {
}
