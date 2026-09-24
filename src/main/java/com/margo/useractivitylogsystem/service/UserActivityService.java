package com.margo.useractivitylogsystem.service;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import com.margo.useractivitylogsystem.mapper.UserActivityMapper;
import com.margo.useractivitylogsystem.model.ActivityRequest;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import com.margo.useractivitylogsystem.repository.UserActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserActivityRepository repository;
    private final CassandraTemplate cassandraTemplate;
    private final UserActivityMapper userActivityMapper;

    @Value("${activity.default-ttl}")
    private Duration defaultActivityTtl;

    public ActivityResponse saveActivity(UUID userId, ActivityRequest request) {
        UserActivity activity = formUserActivity(userId, request);

        Duration effectiveTtl = request.ttlInSeconds() == null
                ? defaultActivityTtl
                : Duration.ofSeconds(request.ttlInSeconds());

        cassandraTemplate.insert(activity, InsertOptions.builder().ttl(effectiveTtl).build());
        log.debug("Saving user activity {} with ttl of {} seconds", activity, effectiveTtl.toSeconds());

        return userActivityMapper.toResponse(activity);
    }

    public List<ActivityResponse> getUserActivities(UUID userId, Integer limit, Instant from, Instant to) {
        if (from == null && to == null) {
            return limit == null ? getAllActivities(userId) : getRecentActivities(userId, limit);
        }

        if (from == null || to == null) {
            throw new IllegalArgumentException("Parameters 'from' and 'to' must be specified together.");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Parameter 'from' cannot be after 'to'.");
        }

        return getActivitiesInTimeRange(userId, from, to);
    }

    private List<ActivityResponse> getAllActivities(UUID userId) {
        return userActivityMapper.toResponse(
                repository.findByKey_UserId(userId));
    }

    private List<ActivityResponse> getActivitiesInTimeRange(UUID userId, Instant from, Instant to) {
        return userActivityMapper.toResponse(
                repository.findByUserIdAndTimeRange(userId, from, to));
    }

    private List<ActivityResponse> getRecentActivities(UUID userId, int limit) {
        return userActivityMapper.toResponse(
                repository.findByKey_UserId(userId, Limit.of(limit)));
    }

    private UserActivity formUserActivity(UUID userId, ActivityRequest request) {
        UUID activityId = Uuids.timeBased();
        Instant timestamp = Instant.ofEpochMilli(Uuids.unixTimestamp(activityId));
        UserActivityKey key = new UserActivityKey(userId, timestamp, activityId);
        return new UserActivity(key, request.activityType(), request.details());
    }
}
