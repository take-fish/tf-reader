package com.fishmatrix.reader.model;

public record BookmarkRequest(
        String userId,
        int chapterIndex,
        int paragraphIndex,
        String excerpt,
        String note) {
}
