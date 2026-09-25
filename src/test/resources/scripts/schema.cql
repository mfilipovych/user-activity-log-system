CREATE KEYSPACE IF NOT EXISTS activity_test
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

USE activity_test;

CREATE TABLE IF NOT EXISTS user_activities (
   user_id uuid,
   activity_timestamp timestamp,
   activity_id timeuuid,
   activity_type text,
   details text,
   PRIMARY KEY (user_id, activity_timestamp, activity_id)
) WITH CLUSTERING ORDER BY (activity_timestamp DESC, activity_id DESC)
   AND DEFAULT_TIME_TO_LIVE = 2592000
   AND COMPACTION = {
            'class': 'TimeWindowCompactionStrategy',
            'compaction_window_unit': 'DAYS',
            'compaction_window_size': 1
    };