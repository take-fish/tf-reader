package com.fishmatrix.reader.util;

import com.fishmatrix.common.error.ApiException;

public final class Validation {

    private Validation() {}

    public static void requireChapterIndex(int chapterIndex, int chapterCount) {
        if (chapterIndex < 0 || chapterIndex >= chapterCount) {
            throw ApiException.badRequest("章节不存在");
        }
    }

    public static String text(String value, int maxLength, String field, boolean required) {
        String cleaned = value == null ? "" : value.strip();
        if (required && cleaned.isEmpty()) {
            throw ApiException.badRequest(field + "不能为空");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            throw ApiException.badRequest(field + "过长");
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
