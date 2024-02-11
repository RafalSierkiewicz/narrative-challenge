CREATE TABLE IF NOT EXISTS event_metrics (
    timestamp TIMESTAMP WITH TIME ZONE,
    user_count BIGINT,
    events_count TEXT,
    PRIMARY KEY (timestamp)
);