package com.fishmatrix.reader.model;

import java.util.List;

public record BookDetail(BookSummary book, List<ChapterSummary> chapters) {}
