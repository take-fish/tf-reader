package com.fishmatrix.reader.util;

import java.util.regex.Pattern;

public final class Validation {

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private Validation() {
    }

    public static void requireUserId(String userId) {
        if (userId == null || !USER_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("userId 格式不合法");
        }
    }

    public static void requireChapterIndex(int chapterIndex, int chapterCount) {
        if (chapterIndex < 0 || chapterIndex >= chapterCount) {
            throw new IllegalArgumentException("章节不存在");
        }
    }

    public static String text(String value, int maxLength, String field, boolean required) {
        String cleaned = value == null ? "" : value.strip();
        if (required && cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            throw new IllegalArgumentException(field + "过长");
        }
        return cleaned;
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip();
        if (cleaned.codePointCount(0, cleaned.length()) <= maxLength) {
            return cleaned;
        }
        int end = cleaned.offsetByCodePoints(0, maxLength);
        return cleaned.substring(0, end);
    }

    public static double clampRatio(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.min(1, Math.max(0, value));
    }
}
