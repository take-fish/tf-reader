package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_bookmark")
public class ReaderBookmark {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String excerpt;
    private String note;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
