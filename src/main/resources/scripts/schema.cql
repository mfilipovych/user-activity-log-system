CREATE KEYSPACE IF NOT EXISTS log_system
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 3
};

USE log_system;

CREATE TABLE IF NOT EXISTS user_activities (
    user_id uuid,
    activity_id timeuuid,
    activity_type text,
    activity_timestamp timestamp,
    details text,
    PRIMARY KEY ( (user_id), activity_timestamp, activity_id )
) WITH CLUSTERING ORDER BY (activity_timestamp DESC, activity_id DESC )
    AND DEFAULT_TIME_TO_LIVE = 2592000;
