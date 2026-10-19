package com.fishmatrix.reader.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Texts {

    private Texts() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        return text.trim();
    }

    public static int wordCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return (int) content.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    public static int paragraphCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String normalized = normalize(content);
        long blocks = Arrays.stream(normalized.split("\n\\s*\n"))
                .filter(block -> !block.isBlank())
                .count();
        if (blocks > 1) {
            return (int) blocks;
        }
        return (int) normalized.lines().filter(line -> !line.isBlank()).count();
    }

    /**
     * 中文 txt 常见 UTF-8 / GBK 两种编码；先严格按 UTF-8 解码，失败回退 GBK。
     */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            try {
                Charset gbk = Charset.forName("GBK");
                CharBuffer decoded = gbk.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                        .decode(ByteBuffer.wrap(bytes));
                return decoded.toString();
            } catch (CharacterCodingException fallback) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }
}
