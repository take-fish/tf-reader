package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("reader_chapter")
public class ReaderChapter {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Integer chapterIndex;
    private String title;
    private String content;
    private Integer wordCount;
}
