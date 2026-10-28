package com.fishmatrix.reader.model;

import java.time.Instant;

public record Annotation(
        long id, int paragraphIndex, String userId, String nickname, String content, Instant createdAt) {}
