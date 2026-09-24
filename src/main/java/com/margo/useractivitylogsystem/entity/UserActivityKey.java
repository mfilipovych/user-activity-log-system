package com.margo.useractivitylogsystem.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@PrimaryKeyClass
@AllArgsConstructor
public class UserActivityKey implements Serializable {
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "activity_timestamp", type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING, ordinal = 0)
    private Instant activityTimestamp;

    @PrimaryKeyColumn(name = "activity_id", type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING, ordinal = 1)
    @CassandraType(type = CassandraType.Name.TIMEUUID)
    private UUID activityId;
}
