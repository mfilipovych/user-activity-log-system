package com.margo.useractivitylogsystem.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivityRequest(
        @NotBlank(message = "Activity type is required")
        @Size(max = 50, message = "Activity type cannot exceed 50 characters")
        String activityType,

        @Size(max = 2000, message = "Details cannot exceed 2000 characters")
        String details
) {
    @Override
    public String toString() {
        return "ActivityRequest{" +
                "activityType='" + activityType + '\'' +
                ", details='" + (details == null ? "not_specified" : "<" + details.length() + " chars>") + '}';
    }
}
