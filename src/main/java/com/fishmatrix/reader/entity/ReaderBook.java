package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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

    /** 乐观锁：并发编辑时由 MyBatis-Plus 校验版本，冲突则更新 0 行。 */
    @Version
    private Integer version;

    /** 软删除标记：有值即视为已删除，所有读取查询都要带 deleted_at IS NULL。 */
    private Instant deletedAt;

    /** 分享码过期时间；空表示该 token 由旧版本生成，未设过期。 */
    private Instant shareTokenExpiresAt;

    /** 非表字段：JOIN reader_category 得到，由 XML 填充。 */
    @TableField(exist = false)
    private String categoryName;

    public boolean isPublic() {
        return scope != null && scope.isPublic();
    }

    public boolean isPrivate() {
        return scope != null && scope.isPrivate();
    }

    public boolean isVisible() {
        return status != null && status.isVisible();
    }

    public boolean isOwnedBy(long userId) {
        return ownerId != null && ownerId == userId;
    }

    public int chapterCountOrZero() {
        return chapterCount == null ? 0 : chapterCount;
    }
}
