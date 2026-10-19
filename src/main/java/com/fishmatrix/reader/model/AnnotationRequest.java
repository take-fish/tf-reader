package com.fishmatrix.reader.model;

public record AnnotationRequest(
        String userId,
        String nickname,
        int paragraphIndex,
        String content) {
}
