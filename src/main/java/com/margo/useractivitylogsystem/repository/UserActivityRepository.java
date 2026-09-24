package com.margo.useractivitylogsystem.repository;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Consistency;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends CassandraRepository<UserActivity, UserActivityKey> {

    @Consistency(value = DefaultConsistencyLevel.LOCAL_ONE)
    List<UserActivity> findByKey_UserId(UUID keyUserId);

    List<UserActivity> findByKey_UserId(UUID keyUserId, Limit limit);

    @Consistency(value = DefaultConsistencyLevel.LOCAL_ONE)
    List<UserActivity> findByKey_UserIdAndKey_ActivityTimestampGreaterThanEqualAndKey_ActivityTimestampLessThan(
            UUID userId, Instant start, Instant end);
}
