package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 复合主键 (shelf_id, book_id)：不设 @TableId，写操作走 XML。 */
@Data
@TableName("reader_shelf_book")
public class ReaderShelfBook {

    private Long shelfId;
    private Long bookId;

    @TableField(value = "added_at", fill = FieldFill.INSERT)
    private Instant addedAt;
}
