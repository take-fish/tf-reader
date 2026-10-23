package com.fishmatrix.reader.model;

public record BookUpdateRequest(String title, String author, String description,
        Long categoryId, String status) {
}
