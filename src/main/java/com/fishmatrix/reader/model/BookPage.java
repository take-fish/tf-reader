package com.fishmatrix.reader.model;

import java.util.List;

public record BookPage(List<BookSummary> books, long total, int page, int size) {}
