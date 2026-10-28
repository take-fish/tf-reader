package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderChapter;
import com.fishmatrix.reader.model.ChapterRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReaderChapterMapper extends BaseMapper<ReaderChapter> {

    /** 批量写入章节：单本 25MiB 的 txt 可能上千章，逐条 insert 是 N 次往返。 */
    int insertChapters(@Param("bookId") long bookId, @Param("chapters") List<ChapterRow> chapters);
}
