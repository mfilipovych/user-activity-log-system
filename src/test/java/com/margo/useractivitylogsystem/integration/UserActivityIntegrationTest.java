package com.margo.useractivitylogsystem.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import com.margo.useractivitylogsystem.model.ActivityRequest;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import com.margo.useractivitylogsystem.repository.UserActivityRepository;
import com.margo.useractivitylogsystem.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.margo.useractivitylogsystem.data.TestData.ACTIVITY_TYPE;
import static com.margo.useractivitylogsystem.data.TestData.DETAILS;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cassandra.keyspace-name=activity_test"
})
class UserActivityIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-09-01T00:00:00Z");

    @Container
    @ServiceConnection
    static CassandraContainer cassandra = new CassandraContainer("cassandra:5.0")
            .withInitScript("scripts/schema.cql");

    @Autowired
    private UserActivityService service;

    @Autowired
    private UserActivityRepository repository;

    @Autowired
    private CqlSession cqlSession;

    @Value("${activity.default-ttl}")
    private Duration defaultTtl;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 60, 3600})
    void saveActivity_persistsRowWithTimeUuidKeyAndExpectedTtl(int requestedTtl) {
        // given
        Integer ttl = requestedTtl == -1 ? null : requestedTtl;
        long expectedTtl = ttl == null ? defaultTtl.toSeconds() : ttl;
        ActivityRequest request = new ActivityRequest(ACTIVITY_TYPE, DETAILS, ttl);

        // when
        ActivityResponse response = service.saveActivity(userId, request);

        // then
        assertThat(repository.findByKey_UserId(userId)).singleElement().satisfies(stored -> {
            UserActivityKey key = stored.getKey();
            assertThat(key.getActivityId()).isEqualTo(response.activityId());
            assertThat(key.getActivityId().version()).isEqualTo(1);
            assertThat(key.getActivityTimestamp()).isEqualTo(response.activityTimestamp());
            assertThat(stored.getActivityType()).isEqualTo(request.activityType());
            assertThat(stored.getDetails()).isEqualTo(request.details());
        });
        assertThat(remainingTtlSeconds()).isBetween((int) expectedTtl - 60, (int) expectedTtl);
        assertThat(service.getUserActivities(userId, null, null, null)).containsExactly(response);
    }

    @ParameterizedTest
    @MethodSource("readCases")
    void getUserActivities_returnsExpectedActivitiesNewestFirst(
            Integer limit, Instant from, Instant to, List<Integer> expectedHours) {
        // given:
        repository.saveAll(IntStream.range(0, 5)
                .mapToObj(hour -> activityAt(userId, at(hour), "TYPE_" + hour))
                .toList());
        repository.save(activityAt(UUID.randomUUID(), at(0), "OTHER_USER"));

        // when
        List<ActivityResponse> result = service.getUserActivities(userId, limit, from, to);

        // then
        assertThat(result)
                .extracting(ActivityResponse::activityType)
                .containsExactlyElementsOf(expectedHours.stream().map(hour -> "TYPE_" + hour).toList());
    }

    @Test
    void findByTimestampRange_returnsRecordsInCorrectRange() {
        // given
        UUID testUser = UUID.randomUUID();
        Instant now = Instant.now();

        UserActivity past = activityAt(testUser, now.minusSeconds(3600), "PAST");
        UserActivity inside = activityAt(testUser, now, "INSIDE");
        UserActivity future = activityAt(testUser, now.plusSeconds(3600), "FUTURE");

        repository.saveAll(List.of(past, inside, future));

        // when
        List<UserActivity> result = repository
                .findByKey_UserIdAndKey_ActivityTimestampGreaterThanEqualAndKey_ActivityTimestampLessThan(
                        testUser, now.minusSeconds(60), now.plusSeconds(60));

        // then
        assertThat(result)
                .singleElement()
                .extracting(UserActivity::getActivityType)
                .isEqualTo("INSIDE");
    }

    private static Stream<Arguments> readCases() {
        return Stream.of(
                // limit, from, to, expected hours (newest first)
                Arguments.of(null, null, null, List.of(4, 3, 2, 1, 0)),   // all
                Arguments.of(2, null, null, List.of(4, 3)),              // recent, limit 2
                Arguments.of(null, at(1), at(3), List.of(2, 1)),         // from inclusive, to exclusive
                Arguments.of(null, at(0), at(5), List.of(4, 3, 2, 1, 0)),// range covering everything
                Arguments.of(null, at(10), at(11), List.of()));          // range with no data
    }

    private static Instant at(int hours) {
        return BASE.plus(hours, ChronoUnit.HOURS);
    }

    private static UserActivity activityAt(UUID userId, Instant time, String type) {
        UserActivityKey key = new UserActivityKey(userId, time, Uuids.startOf(time.toEpochMilli()));
        return new UserActivity(key, type, DETAILS);
    }

    private int remainingTtlSeconds() {
        return cqlSession.execute(SimpleStatement.newInstance(
                        "SELECT TTL(activity_type) FROM user_activities WHERE user_id = ?", userId))
                .one()
                .getInt(0);
    }
}
