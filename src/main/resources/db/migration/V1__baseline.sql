CREATE TABLE reader_book (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(128) NOT NULL DEFAULT '',
    description VARCHAR(1024) NOT NULL DEFAULT '',
    chapter_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_chapter (book_id, chapter_index)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_progress (
    user_id VARCHAR(64) NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL DEFAULT 0,
    scroll_ratio DOUBLE NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, book_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_bookmark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    paragraph_index INT NOT NULL,
    excerpt VARCHAR(512) NOT NULL DEFAULT '',
    note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_bookmark (user_id, book_id, chapter_index, paragraph_index),
    KEY idx_reader_bookmark_book (book_id, chapter_index)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_annotation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    paragraph_index INT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reader_annotation_chapter (book_id, chapter_index, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
