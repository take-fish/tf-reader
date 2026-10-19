package com.fishmatrix.reader.model;

import java.time.Instant;

public record ReadingProgress(int chapterIndex, double scrollRatio, Instant updatedAt) {
}
