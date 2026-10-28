package com.fishmatrix.reader.model;

import jakarta.validation.constraints.Min;

/**
 * 阅读心跳。前端在阅读器可见时每分钟发一次，服务端用两次心跳的时间差计算真实阅读时长，
 * 因此这里**不接受**前端自报的时长。
 */
public record ReadingHeartbeatRequest(@Min(value = 1, message = "bookId 不合法") long bookId) {}
