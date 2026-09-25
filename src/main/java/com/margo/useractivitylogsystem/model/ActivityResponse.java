package com.margo.useractivitylogsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder(toBuilder = true)
public record ActivityResponse(
        UUID userId,
        Instant activityTimestamp,
        UUID activityId,
        String activityType,
        String details
) {
}
