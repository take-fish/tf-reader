-- 经验值与等级。
--
-- 规则（与产品约定一致）：
--   * 上传一本书 +1 经验，累计上限 20 点（一辈子）
--   * 阅读时长每满 20 分钟 +1 经验
--   * 自己上传的书被别人阅读，每满 1 小时给上传者 +1 经验
--   * 等级 9 → 0，0 最高：level = max(0, 9 - experience / 10)，满级后经验继续累计
--
-- 两张表：
--   reader_user_stats   每用户一行，累计统计
--   reader_reading_stat 每对（读者, 书）一行，用于按小时给 owner 结算，避免重复计数

CREATE TABLE reader_user_stats (
    user_id BIGINT NOT NULL,
    experience INT NOT NULL DEFAULT 0,
    user_level TINYINT NOT NULL DEFAULT 9,
    upload_count INT NOT NULL DEFAULT 0,
    reading_minutes BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_reader_user_stats_experience (experience)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reader_reading_stat (
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    minutes INT NOT NULL DEFAULT 0,
    rewarded_hours INT NOT NULL DEFAULT 0,
    last_heartbeat_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, book_id),
    KEY idx_reader_reading_stat_book (book_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
