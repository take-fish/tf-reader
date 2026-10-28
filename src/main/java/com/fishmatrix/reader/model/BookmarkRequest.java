package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 新增书签。身份取自网关鉴权头，请求体不再接受自报的 userId。
 */
public record BookmarkRequest(
        @Min(value = 0, message = "章节索引不合法") int chapterIndex,
        @Min(value = 0, message = "段落索引不合法") int paragraphIndex,
        @Size(max = 512, message = "摘录不能超过 512 个字符") String excerpt,
        @Size(max = 500, message = "批注不能超过 500 个字符") String note) {}
