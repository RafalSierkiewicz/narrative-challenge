CREATE TABLE IF NOT EXISTS event_metrics (
    timestamp timestamp with time zone,
    user_count bigint,
    events_count text
);

CREATE INDEX timestamp_metrics_idx ON event_metrics(timestamp);