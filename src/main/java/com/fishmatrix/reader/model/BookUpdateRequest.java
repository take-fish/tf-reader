package com.fishmatrix.reader.model;

import com.fishmatrix.reader.domain.BookStatus;
import jakarta.validation.constraints.Size;

/**
 * 书籍元信息增量更新：字段为 {@code null} 表示不修改。
 * 长度上限在此声明；"去空白后必填"这类业务语义仍由 service 的 {@code Validation.text} 负责。
 */
public record BookUpdateRequest(
        @Size(max = 255, message = "书名不能超过 255 个字符") String title,
        @Size(max = 128, message = "作者不能超过 128 个字符") String author,
        @Size(max = 1024, message = "简介不能超过 1024 个字符") String description,
        Long categoryId,
        BookStatus status) {}
