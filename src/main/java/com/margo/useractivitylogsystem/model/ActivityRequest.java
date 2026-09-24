package com.margo.useractivitylogsystem.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivityRequest(
        @NotBlank(message = "Activity type is required")
        @Size(max = 50, message = "Activity type cannot exceed 50 characters")
        String activityType,

        @Size(max = 2000, message = "Details cannot exceed 2000 characters")
        String details,

        @Min(value = 60, message = "TTL must be at least 60 seconds (1 minute)")
        @Max(value = 31_536_000, message = "TTL cannot exceed 31,536,000 seconds (1 year)")
        Integer ttlInSeconds
) {
    @Override
    public String toString() {
        return "ActivityRequest{" +
                "activityType='" + activityType + '\'' +
                ", details='" + (details == null ? "not_specified" : "<" + details.length() + " chars>") + '\'' +
                ", ttlInSeconds=" + ttlInSeconds +
                '}';
    }
}
