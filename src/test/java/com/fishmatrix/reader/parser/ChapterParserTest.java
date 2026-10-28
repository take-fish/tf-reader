package com.fishmatrix.reader.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChapterParserTest {

    private final ChapterParser parser = new ChapterParser();

    @Test
    void parsesChineseChapterHeadings() {
        String text = "第一章 起始\n开篇内容。\n\n第二章 推进\n第二章内容。\n\n第3章 收束\n结尾。";

        List<ChapterParser.ParsedChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(3);
        assertThat(chapters)
                .extracting(ChapterParser.ParsedChapter::title)
                .containsExactly("第一章 起始", "第二章 推进", "第3章 收束");
        assertThat(chapters.get(1).content()).isEqualTo("第二章内容。");
    }

    @Test
    void keepsPreambleAsFirstChapter() {
        String text = "这是一个没有标题的前言。\n\n第一章 开始\n正文。";

        List<ChapterParser.ParsedChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(0).title()).isEqualTo("前言");
        assertThat(chapters.get(0).content()).contains("没有标题的前言");
        assertThat(chapters.get(1).title()).isEqualTo("第一章 开始");
    }

    @Test
    void fallsBackToSingleChapter() {
        List<ChapterParser.ParsedChapter> chapters = parser.parse("只有一段正文，没有章节标题。");

        assertThat(chapters).hasSize(1);
        assertThat(chapters.get(0).title()).isEqualTo("正文");
        assertThat(chapters.get(0).wordCount()).isEqualTo(14);
    }

    @Test
    void normalizesBomAndCrlf() {
        String text = "﻿第一章 开始\r\n内容一。\r\n\r\n第2节 继续\r\n内容二。";

        List<ChapterParser.ParsedChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(0).title()).isEqualTo("第一章 开始");
        assertThat(chapters.get(0).content()).isEqualTo("内容一。");
        assertThat(chapters.get(1).title()).isEqualTo("第2节 继续");
    }

    @Test
    void returnsEmptyForBlankText() {
        assertThat(parser.parse("   \n\n ")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }
}
