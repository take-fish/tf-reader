package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fishmatrix.reader.domain.BookScope;
import com.fishmatrix.reader.domain.BookStatus;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_book")
public class ReaderBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;
    private String author;
    private String description;
    private Integer chapterCount;
    private BookScope scope;
    private Long ownerId;
    private String ownerNickname;
    private Long categoryId;
    private BookStatus status;
    private String shareToken;
    private String storageKey;
    private String sourceFilename;
    private Long fileSize;
    private String sha256;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 非表字段：JOIN reader_category 得到，由 XML 填充。 */
    @TableField(exist = false)
    private String categoryName;
}
