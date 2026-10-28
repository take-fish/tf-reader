package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复合主键表（无 @TableId）：不要使用 selectById / updateById / deleteById，
 * 这些方法在无主键实体上不可用，条件查询与删除一律走 Wrapper。
 */
@Mapper
public interface ReaderProgressMapper extends BaseMapper<ReaderProgress> {

    /**
     * 进度是复合主键 (user_id, book_id)，MyBatis-Plus 的 saveOrUpdate 依赖单主键，用不了；
     * 这里直接用 MySQL 的 ON DUPLICATE KEY UPDATE，保证"有则更新、无则插入"是一次原子操作。
     */
    @Insert(
            """
            INSERT INTO reader_progress (user_id, book_id, chapter_index, scroll_ratio, updated_at)
            VALUES (#{userId}, #{bookId}, #{chapterIndex}, #{scrollRatio}, #{updatedAt})
            ON DUPLICATE KEY UPDATE chapter_index = #{chapterIndex},
                                    scroll_ratio = #{scrollRatio},
                                    updated_at = #{updatedAt}
            """)
    int upsert(ReaderProgress progress);
}
