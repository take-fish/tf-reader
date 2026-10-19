package com.fishmatrix.reader.service;

import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.parser.ChapterParser;
import com.fishmatrix.reader.parser.ChapterParser.ParsedChapter;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.util.Texts;
import com.fishmatrix.reader.util.Validation;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LibraryService {

    private final BookRepository bookRepository;
    private final ChapterParser chapterParser;
    private final long maxImportBytes;

    public LibraryService(
            BookRepository bookRepository,
            ChapterParser chapterParser,
            @Value("${app.reader.max-import-bytes:8388608}") long maxImportBytes) {
        this.bookRepository = bookRepository;
        this.chapterParser = chapterParser;
        this.maxImportBytes = maxImportBytes;
    }

    public List<BookSummary> listBooks() {
        return bookRepository.findAll();
    }

    public BookDetail getBook(long bookId) {
        BookSummary book = requireBook(bookId);
        List<ChapterSummary> chapters = bookRepository.findChapters(bookId);
        return new BookDetail(
                book.id(),
                book.title(),
                book.author(),
                book.description(),
                book.chapterCount(),
                chapters);
    }

    public ChapterContent getChapter(long bookId, int chapterIndex) {
        BookSummary book = requireBook(bookId);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        return bookRepository.findChapter(bookId, chapterIndex)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
    }

    @Transactional
    public BookSummary importBook(String title, String author, String fileName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 txt 文件");
        }
        if (file.getSize() > maxImportBytes) {
            throw new IllegalArgumentException("文件大小超过限制");
        }
        String text;
        try {
            text = Texts.decode(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败");
        }
        List<ParsedChapter> chapters = chapterParser.parse(text);
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("未解析到有效章节");
        }

        String fallbackTitle = stripExtension(fileName == null ? "" : fileName);
        String cleanTitle = Validation.truncate(
                title == null || title.isBlank() ? fallbackTitle : title,
                255);
        if (cleanTitle.isEmpty()) {
            cleanTitle = "未命名代码仓库";
        }
        String cleanAuthor = Validation.truncate(author, 128);
        String description = Validation.truncate(
                "导入文本 · " + chapters.size() + " 章 · " + Texts.wordCount(text) + " 字",
                1024);

        Instant now = Instant.now();
        long bookId = bookRepository.insertBook(
                cleanTitle,
                cleanAuthor,
                description,
                chapters.size(),
                now);
        for (int i = 0; i < chapters.size(); i++) {
            ParsedChapter chapter = chapters.get(i);
            String chapterTitle = chapter.title().isBlank() ? "第 " + (i + 1) + " 节" : chapter.title();
            bookRepository.insertChapter(
                    bookId,
                    i,
                    Validation.truncate(chapterTitle, 255),
                    chapter.content(),
                    chapter.wordCount());
        }
        return requireBook(bookId);
    }

    private BookSummary requireBook(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("书籍不存在"));
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
