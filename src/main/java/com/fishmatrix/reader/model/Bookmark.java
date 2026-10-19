package com.fishmatrix.reader.model;

import java.time.Instant;

public record Bookmark(
        long id,
        int chapterIndex,
        int paragraphIndex,
        String excerpt,
        String note,
        Instant createdAt) {
}
