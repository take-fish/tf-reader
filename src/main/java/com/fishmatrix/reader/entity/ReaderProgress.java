package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 复合主键 (user_id, book_id)：不设 @TableId，写操作走 XML。 */
@Data
@TableName("reader_progress")
public class ReaderProgress {

    private Long userId;
    private Long bookId;
    private Integer chapterIndex;
    private Double scrollRatio;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
