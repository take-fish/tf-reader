package com.fishmatrix.reader.model;

import java.util.List;

public record ShelfDetail(Shelf shelf, List<BookSummary> books) {
}
