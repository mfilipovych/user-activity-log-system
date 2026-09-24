package com.margo.useractivitylogsystem.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Setter
@Getter
@ToString
@AllArgsConstructor
@Table("user_activities")
public class UserActivity {

    @PrimaryKey
    private UserActivityKey key;

    @Column("activity_type")
    private String activityType;

    @ToString.Exclude
    private String details;
}
