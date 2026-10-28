package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Min;

/**
 * 保存阅读进度。身份取自网关鉴权头，请求体不再接受自报的 userId。
 * 滚动比例越界时由服务端夹紧而不是报错，保持"能读下去"优先。
 */
public record ProgressRequest(@Min(value = 0, message = "章节索引不合法") int chapterIndex, double scrollRatio) {}
