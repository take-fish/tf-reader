-- 公共库 / 个人私有库、分类、书单与私有内容访问申请。
-- 历史书籍全部视为公共库、无归属人，保持现有阅读链接可用。

ALTER TABLE reader_book
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' AFTER chapter_count,
    ADD COLUMN owner_id BIGINT NULL AFTER scope,
    ADD COLUMN owner_nickname VARCHAR(32) NOT NULL DEFAULT '' AFTER owner_id,
    ADD COLUMN category_id BIGINT NULL AFTER owner_nickname,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE' AFTER category_id,
    ADD COLUMN share_token VARCHAR(64) NULL AFTER status,
    ADD COLUMN source_filename VARCHAR(255) NOT NULL DEFAULT '' AFTER share_token,
    ADD COLUMN storage_key VARCHAR(512) NULL AFTER source_filename,
    ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0 AFTER storage_key,
    ADD COLUMN sha256 CHAR(64) NULL AFTER file_size,
    ADD UNIQUE KEY uk_reader_book_share_token (share_token),
    ADD KEY idx_reader_book_public (scope, status, category_id, updated_at),
    ADD KEY idx_reader_book_owner (owner_id, updated_at);

CREATE TABLE reader_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_shelf (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reader_shelf_owner (owner_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_shelf_book (
    shelf_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    added_at DATETIME(3) NOT NULL,
    PRIMARY KEY (shelf_id, book_id),
    KEY idx_reader_shelf_book_book (book_id),
    CONSTRAINT fk_reader_shelf_book_shelf FOREIGN KEY (shelf_id)
        REFERENCES reader_shelf (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 同一本书同一申请人只保留一条状态记录，避免并发重复申请。
CREATE TABLE reader_access_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requester_nickname VARCHAR(32) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    message VARCHAR(255) NOT NULL DEFAULT '',
    decision_message VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_access_book_requester (book_id, requester_id),
    KEY idx_reader_access_owner (owner_id, status, updated_at),
    KEY idx_reader_access_requester (requester_id, status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
