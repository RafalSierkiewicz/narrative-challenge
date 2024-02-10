CREATE TABLE IF NOT EXISTS events (
    user_id text,
    event text,
    at timestamp with time zone
);

CREATE INDEX timestamp_idx ON events(at);