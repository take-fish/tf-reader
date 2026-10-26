package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 注意：user_id 在 V3 迁移前，数据库列仍为 VARCHAR(64)（值为 "u"+id）。
 * 本实体按 V3 之后的目标 schema（BIGINT）建模，V3 执行前不要用它做真实读写。
 */
@Data
@TableName("reader_annotation")
public class ReaderAnnotation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private Long userId;
    private String nickname;
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
