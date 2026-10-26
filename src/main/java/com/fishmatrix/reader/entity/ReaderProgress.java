package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 复合主键 (user_id, book_id)：不设 @TableId，写操作走 XML。
 *
 * 注意：user_id 在 V3 迁移前，数据库列仍为 VARCHAR(64)（值为 "u"+id）。
 * 本实体按 V3 之后的目标 schema（BIGINT）建模，V3 执行前不要用它做真实读写。
 */
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
