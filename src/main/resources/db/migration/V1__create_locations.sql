-- Saved locations (a church, school, or any place that wants its Sabbath times on file).
-- Written in SQL that both PostgreSQL (production) and H2 (tests) accept.
-- The checks repeat the API's validation so bad rows can't get in by any other route.

CREATE TABLE locations (
    id          UUID                     NOT NULL,
    name        VARCHAR(200)             NOT NULL,
    latitude    DOUBLE PRECISION         NOT NULL,
    longitude   DOUBLE PRECISION         NOT NULL,
    time_zone   VARCHAR(64)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT                   NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT ck_locations_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_locations_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_locations_longitude CHECK (longitude BETWEEN -180 AND 180)
);

-- The list endpoint pages through locations ordered by name, then id.
CREATE INDEX ix_locations_name ON locations (name, id);
