package com.fishmatrix.reader.model;

/** 阅读进度响应。没有进度时 {@code progress} 为 {@code null}，而不是省略字段。 */
public record ProgressResponse(ReadingProgress progress) {
}
