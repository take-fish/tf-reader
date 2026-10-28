package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增章节批注。身份与昵称都取自鉴权上下文，请求体不再接受自报字段。
 */
public record AnnotationRequest(
        @Min(value = 0, message = "段落索引不合法") int paragraphIndex,
        @NotBlank(message = "评审意见不能为空") @Size(max = 500, message = "评审意见不能超过 500 个字符") String content) {}
