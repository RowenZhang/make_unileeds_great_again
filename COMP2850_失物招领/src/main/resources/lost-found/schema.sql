CREATE TABLE IF NOT EXISTS found_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_key UUID NOT NULL UNIQUE,
    item_name VARCHAR(100) NOT NULL,
    found_date DATE NOT NULL,
    collection_point VARCHAR(120) NOT NULL,
    tags VARCHAR(200) NOT NULL,
    photo_type VARCHAR(10),
    photo BLOB,
    CHECK ((photo IS NULL) = (photo_type IS NULL))
);

CREATE INDEX IF NOT EXISTS found_date_index ON found_item(found_date DESC, id DESC);

ALTER TABLE found_item ADD COLUMN IF NOT EXISTS example BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE found_item ADD COLUMN IF NOT EXISTS delete_token UUID DEFAULT RANDOM_UUID() NOT NULL;
CREATE TABLE IF NOT EXISTS example_import (batch VARCHAR(64) PRIMARY KEY);
