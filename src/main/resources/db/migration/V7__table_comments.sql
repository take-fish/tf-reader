-- tf-reader 全表中文注释
-- 只修改表与字段注释，不改变类型、默认值、索引；列定义逐字取自 MySQL 8.4 的 SHOW CREATE TABLE。
-- 列注释统一用 ALGORITHM=INSTANT，若定义与生产不一致会直接报错，避免静默改结构。

ALTER TABLE `reader_book` COMMENT = '阅读书籍：公共库与私有库书籍的元数据';

ALTER TABLE `reader_book`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '书籍主键，自增',
    MODIFY COLUMN `title` varchar(255) NOT NULL COMMENT '书名',
    MODIFY COLUMN `author` varchar(128) NOT NULL DEFAULT '' COMMENT '作者',
    MODIFY COLUMN `description` varchar(1024) NOT NULL DEFAULT '' COMMENT '书籍简介',
    MODIFY COLUMN `chapter_count` int NOT NULL DEFAULT '0' COMMENT '章节总数',
    MODIFY COLUMN `scope` varchar(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见范围，PUBLIC 公共库 / PRIVATE 私有库',
    MODIFY COLUMN `owner_id` bigint DEFAULT NULL COMMENT '上传者用户 ID，公共库也可能为空',
    MODIFY COLUMN `owner_nickname` varchar(32) NOT NULL DEFAULT '' COMMENT '上传者昵称快照',
    MODIFY COLUMN `category_id` bigint DEFAULT NULL COMMENT '分类 ID，关联 reader_category.id',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'VISIBLE' COMMENT '展示状态，VISIBLE 可见 / HIDDEN 已隐藏',
    MODIFY COLUMN `share_token` varchar(64) DEFAULT NULL COMMENT '私有书分享码，为空表示未分享',
    MODIFY COLUMN `source_filename` varchar(255) NOT NULL DEFAULT '' COMMENT '上传时的原始文件名',
    MODIFY COLUMN `storage_key` varchar(512) DEFAULT NULL COMMENT '源文件存储键',
    MODIFY COLUMN `file_size` bigint NOT NULL DEFAULT '0' COMMENT '源文件字节数',
    MODIFY COLUMN `sha256` char(64) DEFAULT NULL COMMENT '源文件 SHA-256，用于去重与校验',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '入库时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，每次更新加 1',
    MODIFY COLUMN `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间，非空表示已删除',
    MODIFY COLUMN `share_token_expires_at` datetime(3) DEFAULT NULL COMMENT '分享码过期时间，为空表示长期有效',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_chapter` COMMENT = '书籍章节：正文按章节拆分存储';

ALTER TABLE `reader_chapter`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '章节主键，自增',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '所属书籍 ID',
    MODIFY COLUMN `chapter_index` int NOT NULL COMMENT '章节序号，从 1 开始',
    MODIFY COLUMN `title` varchar(255) NOT NULL COMMENT '章节标题',
    MODIFY COLUMN `content` mediumtext NOT NULL COMMENT '章节正文',
    MODIFY COLUMN `word_count` int NOT NULL DEFAULT '0' COMMENT '章节字数',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_progress` COMMENT = '阅读进度：每个用户每本书读到哪里';

ALTER TABLE `reader_progress`
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '读者用户 ID',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '书籍 ID',
    MODIFY COLUMN `chapter_index` int NOT NULL DEFAULT '0' COMMENT '最后阅读到的章节序号',
    MODIFY COLUMN `scroll_ratio` double NOT NULL DEFAULT '0' COMMENT '章节内滚动进度，0 到 1',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '进度最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_bookmark` COMMENT = '阅读书签：记录章节、段落与摘录';

ALTER TABLE `reader_bookmark`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '书签主键，自增',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户 ID',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '书籍 ID',
    MODIFY COLUMN `chapter_index` int NOT NULL COMMENT '章节序号',
    MODIFY COLUMN `paragraph_index` int NOT NULL COMMENT '段落序号',
    MODIFY COLUMN `excerpt` varchar(512) NOT NULL DEFAULT '' COMMENT '书签摘录文本',
    MODIFY COLUMN `note` varchar(500) DEFAULT NULL COMMENT '书签备注',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_annotation` COMMENT = '阅读批注：用户对章节段落的评论';

ALTER TABLE `reader_annotation`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批注主键，自增',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '书籍 ID',
    MODIFY COLUMN `chapter_index` int NOT NULL COMMENT '章节序号',
    MODIFY COLUMN `paragraph_index` int NOT NULL COMMENT '段落序号',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '批注用户 ID',
    MODIFY COLUMN `nickname` varchar(32) NOT NULL COMMENT '批注人昵称快照',
    MODIFY COLUMN `content` varchar(500) NOT NULL COMMENT '批注内容',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_category` COMMENT = '书籍分类';

ALTER TABLE `reader_category`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类主键，自增',
    MODIFY COLUMN `name` varchar(64) NOT NULL COMMENT '分类名称，唯一',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，越大越靠前',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_shelf` COMMENT = '用户书架：私有书籍分组';

ALTER TABLE `reader_shelf`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '书架主键，自增',
    MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '书架所有者用户 ID',
    MODIFY COLUMN `name` varchar(64) NOT NULL COMMENT '书架名称',
    MODIFY COLUMN `description` varchar(255) NOT NULL DEFAULT '' COMMENT '书架简介',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，每次更新加 1',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_shelf_book` COMMENT = '书架与书籍的关联';

ALTER TABLE `reader_shelf_book`
    MODIFY COLUMN `shelf_id` bigint NOT NULL COMMENT '书架 ID',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '书籍 ID',
    MODIFY COLUMN `added_at` datetime(3) NOT NULL COMMENT '加入书架时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_access_request` COMMENT = '私有书访问申请：申请、审批与撤销记录';

ALTER TABLE `reader_access_request`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请主键，自增',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '申请访问的书籍 ID',
    MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '书籍所有者用户 ID',
    MODIFY COLUMN `requester_id` bigint NOT NULL COMMENT '申请人用户 ID',
    MODIFY COLUMN `requester_nickname` varchar(32) NOT NULL DEFAULT '' COMMENT '申请人昵称快照',
    MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '申请状态，PENDING 待处理 / APPROVED 已通过 / REJECTED 已拒绝 / CANCELLED 已取消 / REVOKED 已撤销',
    MODIFY COLUMN `message` varchar(255) NOT NULL DEFAULT '' COMMENT '申请留言',
    MODIFY COLUMN `decision_message` varchar(255) NOT NULL DEFAULT '' COMMENT '审批回复',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '申请时间',
    MODIFY COLUMN `decided_at` datetime(3) DEFAULT NULL COMMENT '审批时间，未处理为空',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，每次更新加 1',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_user_stats` COMMENT = '阅读用户统计：经验、等级、上传数与阅读时长';

ALTER TABLE `reader_user_stats`
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户 ID，主键',
    MODIFY COLUMN `experience` int NOT NULL DEFAULT '0' COMMENT '累计经验值',
    MODIFY COLUMN `user_level` tinyint NOT NULL DEFAULT '9' COMMENT '用户等级，9 到 0，0 最高；level = max(0, 9 - experience / 10)',
    MODIFY COLUMN `upload_count` int NOT NULL DEFAULT '0' COMMENT '累计上传书籍数',
    MODIFY COLUMN `reading_minutes` bigint NOT NULL DEFAULT '0' COMMENT '累计阅读分钟数',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `reader_reading_stat` COMMENT = '按书阅读统计：心跳累计时长与已结算给书主的小时数';

ALTER TABLE `reader_reading_stat`
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '读者用户 ID',
    MODIFY COLUMN `book_id` bigint NOT NULL COMMENT '书籍 ID',
    MODIFY COLUMN `minutes` int NOT NULL DEFAULT '0' COMMENT '在该书上的累计阅读分钟数，由心跳差值累计',
    MODIFY COLUMN `rewarded_hours` int NOT NULL DEFAULT '0' COMMENT '已给书主结算的小时数，每满 1 小时加 1',
    MODIFY COLUMN `last_heartbeat_at` datetime(3) DEFAULT NULL COMMENT '最近一次心跳时间',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    ALGORITHM=INSTANT;
