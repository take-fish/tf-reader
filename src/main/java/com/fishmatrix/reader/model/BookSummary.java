package com.fishmatrix.reader.model;

import java.time.Instant;

public record BookSummary(
        long id,
        String title,
        String author,
        String description,
        int chapterCount,
        Instant updatedAt) {
}
