package com.fishmatrix.reader.model;

import com.fishmatrix.reader.domain.BookScope;
import com.fishmatrix.reader.domain.BookStatus;
import java.time.Instant;

/**
 * 书籍列表项。scope/owner/status 决定可见性与可管理性，
 * canDelete/canEdit 由服务端按当前用户计算，前端不自行推断。
 */
public record BookSummary(
        long id,
        String title,
        String author,
        String description,
        int chapterCount,
        BookScope scope,
        Long ownerId,
        String ownerNickname,
        Long categoryId,
        String categoryName,
        BookStatus status,
        boolean mine,
        boolean canEdit,
        boolean canDelete,
        Instant createdAt,
        Instant updatedAt) {
}
