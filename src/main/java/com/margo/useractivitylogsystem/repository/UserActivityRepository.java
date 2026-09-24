package com.margo.useractivitylogsystem.repository;

import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.entity.UserActivityKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends CassandraRepository<UserActivity, UserActivityKey> {
    
    List<UserActivity> findByKey_UserId(UUID keyUserId);
    
    List<UserActivity> findByKey_UserId(UUID keyUserId, Limit limit);

    @Query("SELECT * FROM user_activities " +
            "WHERE user_id = :userId AND activity_timestamp >= :start AND activity_timestamp < :end")
    List<UserActivity> findByUserIdAndTimeRange(@Param("userId") UUID userId,
                                                @Param("start") Instant from,
                                                @Param("end") Instant to);
}
