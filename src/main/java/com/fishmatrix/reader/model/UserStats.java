package com.fishmatrix.reader.model;

/**
 * 用户的经验值概览。
 *
 * <p>{@code level} 从 9 到 0，0 为最高；{@code nextLevelExperience} 是升到下一级所需的总经验，
 * 已经满级时为 {@code null}。</p>
 */
public record UserStats(
        int experience,
        int level,
        Integer nextLevelExperience,
        int uploadCount,
        long readingMinutes) {
}
