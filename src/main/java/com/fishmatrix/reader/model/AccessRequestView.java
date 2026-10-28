package com.fishmatrix.reader.model;

import com.fishmatrix.reader.domain.AccessRequestStatus;
import java.time.Instant;

public record AccessRequestView(
        long id,
        long bookId,
        String bookTitle,
        long ownerId,
        String ownerNickname,
        long requesterId,
        String requesterNickname,
        AccessRequestStatus status,
        String message,
        String decisionMessage,
        Instant createdAt,
        Instant decidedAt) {}
