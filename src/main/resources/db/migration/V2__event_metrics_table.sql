CREATE TABLE IF NOT EXISTS event_metrics (
    timestamp timestamp with time zone,
    user_count bigint,
    events_count text,
    PRIMARY KEY (timestamp)
);