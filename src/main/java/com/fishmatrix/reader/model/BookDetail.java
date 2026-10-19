package com.fishmatrix.reader.model;

import java.util.List;

public record BookDetail(
        long id,
        String title,
        String author,
        String description,
        int chapterCount,
        List<ChapterSummary> chapters) {
}
