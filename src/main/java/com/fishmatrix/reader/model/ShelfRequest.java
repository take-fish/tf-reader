package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Size;

public record ShelfRequest(
        @Size(max = 64, message = "书单名称不能超过 64 个字符") String name,
        @Size(max = 255, message = "书单描述不能超过 255 个字符") String description) {}
