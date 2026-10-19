package com.fishmatrix.reader.model;

public record ProgressRequest(String userId, int chapterIndex, double scrollRatio) {
}
