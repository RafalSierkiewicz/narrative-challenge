CREATE SEQUENCE events_ordering_seq;
CREATE TABLE IF NOT EXISTS events (
    ordering BIGINT DEFAULT nextval('events_ordering_seq'),
    user_id TEXT,
    event TEXT,
    at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (ordering)
);
CREATE INDEX timestamp_idx ON events(at);