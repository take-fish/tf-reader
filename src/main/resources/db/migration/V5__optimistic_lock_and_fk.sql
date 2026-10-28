-- V5: 乐观锁 + 外键
--
-- 一、乐观锁：给三张"会被并发编辑"的表加 version 列，配合 MyBatis-Plus 的
--     OptimisticLockerInnerInterceptor，实现"后写覆盖"能被检测到（更新返回 0 行即冲突）。
--     reader_progress / reader_bookmark 这类按用户隔离的数据不需要。
--
-- 二、外键：此前这些关联全靠代码手工维护删除顺序，漏一处就留孤儿数据。
--     加上外键后由数据库保证引用完整性，删除书籍时的级联也交给 ON DELETE CASCADE。

ALTER TABLE reader_book
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE reader_shelf
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE reader_access_request
    ADD COLUMN version INT NOT NULL DEFAULT 0;

-- 分类被删除时把书置为未分类（原来靠 CategoryService 里一条额外 UPDATE 维持）
ALTER TABLE reader_book
    ADD CONSTRAINT fk_reader_book_category FOREIGN KEY (category_id)
        REFERENCES reader_category (id) ON DELETE SET NULL;

ALTER TABLE reader_chapter
    ADD CONSTRAINT fk_reader_chapter_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;

ALTER TABLE reader_progress
    ADD CONSTRAINT fk_reader_progress_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;

ALTER TABLE reader_bookmark
    ADD CONSTRAINT fk_reader_bookmark_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;

ALTER TABLE reader_annotation
    ADD CONSTRAINT fk_reader_annotation_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;

ALTER TABLE reader_access_request
    ADD CONSTRAINT fk_reader_access_request_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;

ALTER TABLE reader_shelf_book
    ADD CONSTRAINT fk_reader_shelf_book_book FOREIGN KEY (book_id)
        REFERENCES reader_book (id) ON DELETE CASCADE;
