package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @Size(max = 64, message = "分类名称不能超过 64 个字符") String name,
        Integer sortOrder) {
}
