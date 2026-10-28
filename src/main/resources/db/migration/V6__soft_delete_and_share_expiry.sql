-- V6: 书籍软删除 + 分享码有效期
--
-- 一、软删除：reader_book.deleted_at 有值即视为已删除。
--     物理删除会连带章节/进度/书签/批注一起消失且无法恢复；软删除保留数据，
--     所有读取查询统一追加 deleted_at IS NULL。文件（storage_key）也保留，便于恢复。
--
-- 二、分享码有效期：share_token_expires_at 到期后分享链接失效。
--     此前 token 一旦生成就永久有效，且无法吊销。

ALTER TABLE reader_book
    ADD COLUMN deleted_at DATETIME(3) NULL;

ALTER TABLE reader_book
    ADD COLUMN share_token_expires_at DATETIME(3) NULL;
