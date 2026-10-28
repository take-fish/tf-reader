package com.fishmatrix.reader.parser;

import com.fishmatrix.reader.util.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ChapterParser {

    /**
     * 章节标题单独成行，且不含句号/问号/分号等正文标点，避免把「第二章内容。」误判为标题。
     */
    private static final Pattern CHAPTER_TITLE = Pattern.compile(
            "^\\s*(第[0-9０-９一二三四五六七八九十百千万零〇两]+[章回节卷]" + "[^\\n。！？；，,!?;]{0,60})\\s*$", Pattern.MULTILINE);

    public List<ParsedChapter> parse(String raw) {
        String text = Texts.normalize(raw);
        if (text.isEmpty()) {
            return List.of();
        }

        List<ChapterMark> marks = new ArrayList<>();
        Matcher matcher = CHAPTER_TITLE.matcher(text);
        while (matcher.find()) {
            marks.add(new ChapterMark(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }

        if (marks.isEmpty()) {
            return List.of(toChapter("正文", text));
        }

        List<ParsedChapter> chapters = new ArrayList<>();
        ChapterMark first = marks.get(0);
        String preamble = text.substring(0, first.start()).trim();
        if (!preamble.isEmpty()) {
            chapters.add(toChapter("前言", preamble));
        }

        for (int i = 0; i < marks.size(); i++) {
            ChapterMark current = marks.get(i);
            int end = i + 1 < marks.size() ? marks.get(i + 1).start() : text.length();
            String content = text.substring(current.end(), end).trim();
            chapters.add(toChapter(current.title(), content));
        }
        return chapters;
    }

    private ParsedChapter toChapter(String title, String content) {
        String normalized = Texts.normalize(content);
        return new ParsedChapter(title, normalized, Texts.wordCount(normalized));
    }

    private record ChapterMark(String title, int start, int end) {}

    public record ParsedChapter(String title, String content, int wordCount) {}
}
