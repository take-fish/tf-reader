package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_category")
public class ReaderCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer sortOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
