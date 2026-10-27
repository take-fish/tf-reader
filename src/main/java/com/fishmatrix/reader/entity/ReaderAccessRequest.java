package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fishmatrix.reader.domain.AccessRequestStatus;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_access_request")
public class ReaderAccessRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Long ownerId;
    private Long requesterId;
    private String requesterNickname;
    private AccessRequestStatus status;
    private String message;
    private String decisionMessage;
    private Instant decidedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
