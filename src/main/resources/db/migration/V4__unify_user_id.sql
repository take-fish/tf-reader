-- V4: 把用户标识统一成 BIGINT
--
-- 背景：reader_progress / reader_bookmark / reader_annotation 的 user_id 是 VARCHAR(64)，
-- 存的是 "u" + 用户中台数字 id（例如 u11）；而 reader_book.owner_id、reader_shelf.owner_id、
-- 经验值两张表都是 BIGINT。同一个用户在两套表里是两种写法，跨表 JOIN 写不出来，
-- 编译器也拦不住把 "u11" 当 Long 用的错误。
--
-- 转换是纯去前缀：u11 → 11，数字部分本来就是用户 id，不丢任何信息，也不改变归属关系。
-- 无法映射成数字的历史行（早期测试写入的 UUID）先删掉——这类行本来就对应不到任何用户。

DELETE FROM reader_progress
 WHERE user_id NOT REGEXP '^u[0-9]+$';

DELETE FROM reader_bookmark
 WHERE user_id NOT REGEXP '^u[0-9]+$';

DELETE FROM reader_annotation
 WHERE user_id NOT REGEXP '^u[0-9]+$';

UPDATE reader_progress
   SET user_id = SUBSTRING(user_id, 2);

UPDATE reader_bookmark
   SET user_id = SUBSTRING(user_id, 2);

UPDATE reader_annotation
   SET user_id = SUBSTRING(user_id, 2);

ALTER TABLE reader_progress
    MODIFY user_id BIGINT NOT NULL;

ALTER TABLE reader_bookmark
    MODIFY user_id BIGINT NOT NULL;

ALTER TABLE reader_annotation
    MODIFY user_id BIGINT NOT NULL;
