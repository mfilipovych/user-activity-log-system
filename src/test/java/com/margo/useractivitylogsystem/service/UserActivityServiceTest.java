package com.margo.useractivitylogsystem.service;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import com.margo.useractivitylogsystem.mapper.UserActivityMapper;
import com.margo.useractivitylogsystem.model.ActivityRequest;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import com.margo.useractivitylogsystem.repository.UserActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.margo.useractivitylogsystem.data.TestData.ACTIVITY_TYPE;
import static com.margo.useractivitylogsystem.data.TestData.DETAILS;
import static com.margo.useractivitylogsystem.data.TestData.FROM;
import static com.margo.useractivitylogsystem.data.TestData.TO;
import static com.margo.useractivitylogsystem.data.TestData.USER_ID;
import static com.margo.useractivitylogsystem.data.TestData.userActivity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    @Mock
    private UserActivityRepository repository;

    @Mock
    private CassandraTemplate cassandraTemplate;

    @Mock
    private UserActivityMapper userActivityMapper;

    @InjectMocks
    private UserActivityService service;

    @Captor
    private ArgumentCaptor<UserActivity> activityCaptor;

    @Captor
    private ArgumentCaptor<InsertOptions> optionsCaptor;

    private final List<UserActivity> entities = List.of(userActivity());
    private final List<ActivityResponse> responses = List.of(mock(ActivityResponse.class));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultActivityTtl", DEFAULT_TTL);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, 2592000", "60, 60", "3600, 3600"}, nullValues = "NULL")
    void saveActivity_usesRequestedTtlOrFallsBackToDefault(Integer requestedTtl, long expectedSeconds) {
        // given
        ActivityRequest request = new ActivityRequest(ACTIVITY_TYPE, DETAILS, requestedTtl);

        // when
        service.saveActivity(USER_ID, request);

        // then
        verify(cassandraTemplate).insert(any(UserActivity.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getTtl()).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    void saveActivity_persistsTimeUuidKeyAndReturnsMappedResponse() {
        // given
        ActivityRequest request = new ActivityRequest(ACTIVITY_TYPE, DETAILS, null);
        ActivityResponse response = mock(ActivityResponse.class);
        when(userActivityMapper.toResponse(any(UserActivity.class))).thenReturn(response);

        // when
        ActivityResponse result = service.saveActivity(USER_ID, request);

        // then
        verify(cassandraTemplate).insert(activityCaptor.capture(), any(InsertOptions.class));
        UserActivity saved = activityCaptor.getValue();
        UserActivityKey key = saved.getKey();

        assertThat(key.getUserId()).isEqualTo(USER_ID);
        assertThat(key.getActivityId().version()).isEqualTo(1);
        assertThat(key.getActivityTimestamp())
                .isEqualTo(Instant.ofEpochMilli(Uuids.unixTimestamp(key.getActivityId())));
        assertThat(saved.getActivityType()).isEqualTo(ACTIVITY_TYPE);
        assertThat(saved.getDetails()).isEqualTo(DETAILS);

        verify(userActivityMapper).toResponse(saved);
        assertThat(result).isSameAs(response);
    }

    @Test
    void getUserActivities_withoutRangeOrLimit_returnsAll() {
        // given
        when(repository.findByKey_UserId(USER_ID)).thenReturn(entities);
        when(userActivityMapper.toResponse(entities)).thenReturn(responses);

        // when
        List<ActivityResponse> result = service.getUserActivities(USER_ID, null, null, null);

        // then
        assertThat(result).isSameAs(responses);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void getUserActivities_withLimitOnly_returnsRecent(int limit) {
        // given
        when(repository.findByKey_UserId(USER_ID, Limit.of(limit))).thenReturn(entities);
        when(userActivityMapper.toResponse(entities)).thenReturn(responses);

        // when
        List<ActivityResponse> result = service.getUserActivities(USER_ID, limit, null, null);

        // then
        assertThat(result).isSameAs(responses);
    }

    @Test
    void getUserActivities_withValidRange_queriesByTimeRange() {
        // given
        when(repository.findByKey_UserIdAndKey_ActivityTimestampGreaterThanEqualAndKey_ActivityTimestampLessThan(USER_ID, FROM, TO)).thenReturn(entities);
        when(userActivityMapper.toResponse(entities)).thenReturn(responses);

        // when
        List<ActivityResponse> result = service.getUserActivities(USER_ID, null, FROM, TO);

        // then
        assertThat(result).isSameAs(responses);
    }

    @Test
    void getUserActivities_withValidRangeAndLimit_queriesByTimeRange() {
        // given
        int limit = 10;
        when(repository.findByKey_UserIdAndKey_ActivityTimestampGreaterThanEqualAndKey_ActivityTimestampLessThan
                (USER_ID, FROM, TO, Limit.of(limit))).thenReturn(entities);
        when(userActivityMapper.toResponse(entities)).thenReturn(responses);

        // when
        List<ActivityResponse> result = service.getUserActivities(USER_ID, limit, FROM, TO);

        // then
        assertThat(result).isSameAs(responses);
    }

    @ParameterizedTest
    @MethodSource("com.margo.useractivitylogsystem.data.TestData#invalidRanges")
    void getUserActivities_withInvalidRange_throwsAndSkipsFetchingRecords(Instant from, Instant to, String message) {
        // when & then
        assertThatThrownBy(() -> service.getUserActivities(USER_ID, null, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        verifyNoInteractions(repository);
    }
}