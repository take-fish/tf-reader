CREATE TABLE IF NOT EXISTS reader_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS reader_book (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(128) NOT NULL DEFAULT '',
    description VARCHAR(1024) NOT NULL DEFAULT '',
    chapter_count INT NOT NULL DEFAULT 0,
    scope VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    owner_id BIGINT NULL,
    owner_nickname VARCHAR(32) NOT NULL DEFAULT '',
    category_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    share_token VARCHAR(64) NULL,
    source_filename VARCHAR(255) NOT NULL DEFAULT '',
    storage_key VARCHAR(512) NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    sha256 CHAR(64) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (share_token)
);

CREATE INDEX IF NOT EXISTS idx_reader_book_public
    ON reader_book (scope, status, category_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_reader_book_owner
    ON reader_book (owner_id, updated_at);

CREATE TABLE IF NOT EXISTS reader_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content CLOB NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (book_id, chapter_index)
);

CREATE TABLE IF NOT EXISTS reader_progress (
    user_id VARCHAR(64) NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL DEFAULT 0,
    scroll_ratio DOUBLE NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (user_id, book_id)
);

CREATE TABLE IF NOT EXISTS reader_bookmark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    paragraph_index INT NOT NULL,
    excerpt VARCHAR(512) NOT NULL DEFAULT '',
    note VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, book_id, chapter_index, paragraph_index)
);

CREATE TABLE IF NOT EXISTS reader_annotation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    paragraph_index INT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS reader_shelf (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_reader_shelf_owner
    ON reader_shelf (owner_id, updated_at);

CREATE TABLE IF NOT EXISTS reader_shelf_book (
    shelf_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    added_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (shelf_id, book_id),
    CONSTRAINT fk_reader_shelf_book_shelf FOREIGN KEY (shelf_id)
        REFERENCES reader_shelf (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reader_shelf_book_book
    ON reader_shelf_book (book_id);

CREATE TABLE IF NOT EXISTS reader_access_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requester_nickname VARCHAR(32) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    message VARCHAR(255) NOT NULL DEFAULT '',
    decision_message VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL,
    decided_at TIMESTAMP(3) NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (book_id, requester_id)
);

CREATE INDEX IF NOT EXISTS idx_reader_access_owner
    ON reader_access_request (owner_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_reader_access_requester
    ON reader_access_request (requester_id, status, updated_at);

-- V3: 经验值与等级（与 db/migration/V3__reader_experience.sql 保持一致）
CREATE TABLE IF NOT EXISTS reader_user_stats (
    user_id BIGINT NOT NULL,
    experience INT NOT NULL DEFAULT 0,
    user_level TINYINT NOT NULL DEFAULT 9,
    upload_count INT NOT NULL DEFAULT 0,
    reading_minutes BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE INDEX IF NOT EXISTS idx_reader_user_stats_experience
    ON reader_user_stats (experience);

CREATE TABLE IF NOT EXISTS reader_reading_stat (
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    minutes INT NOT NULL DEFAULT 0,
    rewarded_hours INT NOT NULL DEFAULT 0,
    last_heartbeat_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_reader_reading_stat_book
    ON reader_reading_stat (book_id);
