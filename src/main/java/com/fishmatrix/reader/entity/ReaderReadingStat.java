package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 每对（读者, 书）的阅读累计。用于两件事：
 * 按"满 1 小时"给书主结算经验时不重复计数（rewarded_hours），以及用两次心跳的差值算真实阅读时长。
 *
 * <p>复合主键 (user_id, book_id)：不设 @TableId，读写走 Wrapper。</p>
 */
@Data
@TableName("reader_reading_stat")
public class ReaderReadingStat {

    private Long userId;
    private Long bookId;

    /** 累计阅读分钟数，只按整分钟累加。 */
    private Integer minutes;

    /** 已经给书主结算过多少点，用来避免重复发放。 */
    private Integer rewardedHours;

    private Instant lastHeartbeatAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
