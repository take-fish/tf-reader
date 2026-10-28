package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Size;

public record AccessRequestCreate(
        @Size(max = 64, message = "分享码格式不正确") String shareToken,
        @Size(max = 255, message = "申请说明不能超过 255 个字符") String message) {}
