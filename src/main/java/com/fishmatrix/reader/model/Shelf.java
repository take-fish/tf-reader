package com.fishmatrix.reader.model;

import java.time.Instant;

public record Shelf(long id, String name, String description, int bookCount, Instant updatedAt) {}
