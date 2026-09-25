package com.margo.useractivitylogsystem.data;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import org.junit.jupiter.params.provider.Arguments;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;


public final class TestData {
    private TestData() { }

    public static final UUID USER_ID = UUID.randomUUID();
    public static final String ACTIVITY_TYPE = "LOGIN";
    public static final String DETAILS = "some details";
    public static final UUID ACTIVITY_ID = Uuids.timeBased();
    public static final Instant ACTIVITY_TIMESTAMP = Instant.now().minus(Duration.ofDays(30));
    public static final Instant FROM = Instant.now().minus(Duration.ofDays(30));
    public static final Instant TO = Instant.now();

    public static UserActivity userActivity() {
        return new UserActivity(new UserActivityKey(USER_ID, ACTIVITY_TIMESTAMP, ACTIVITY_ID),
                ACTIVITY_TYPE, DETAILS);
    }

    public static ActivityResponse activityResponse() {
        return ActivityResponse.builder()
                .activityId(ACTIVITY_ID)
                .activityType(ACTIVITY_TYPE)
                .details(DETAILS)
                .activityTimestamp(ACTIVITY_TIMESTAMP)
                .userId(USER_ID)
                .build();
    }

    public static Stream<Arguments> invalidRanges() {
        String missingBoundMessage = "Parameters 'from' and 'to' must be specified together.";
        String reversedRangeMessage = "Parameter 'from' must come strictly after 'to'.";
        return Stream.of(
                Arguments.of(FROM, null, missingBoundMessage),
                Arguments.of(null, TO, missingBoundMessage),
                Arguments.of(TO, FROM, reversedRangeMessage),
                Arguments.of(FROM, FROM, reversedRangeMessage));
    }
}
