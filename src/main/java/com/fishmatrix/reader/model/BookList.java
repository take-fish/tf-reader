package com.fishmatrix.reader.model;

import java.util.List;

/**
 * 书籍列表响应。列表接口统一用 {@code {资源名: [...]}} 包一层，单对象接口直接返回对象，
 * 这样前端每个列表都走同一套解包逻辑。
 */
public record BookList(List<BookSummary> books) {
}
