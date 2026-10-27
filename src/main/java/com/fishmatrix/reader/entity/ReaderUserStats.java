package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 每个用户的累计统计。等级由经验推导（见 ExperienceService.levelOf），这里冗余存一份便于排序。 */
@Data
@TableName("reader_user_stats")
public class ReaderUserStats {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer experience;

    /** 数据库列名用 user_level，避开 level 这个通用词。 */
    @TableField("user_level")
    private Integer level;

    private Integer uploadCount;

    private Long readingMinutes;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
