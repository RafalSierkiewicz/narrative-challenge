CREATE SEQUENCE events_ordering_seq;
CREATE TABLE IF NOT EXISTS events (
    ordering bigint default nextval('events_ordering_seq'),
    user_id text,
    event text,
    at timestamp with time zone,
    PRIMARY KEY (ordering)
);
CREATE INDEX timestamp_idx ON events(at);