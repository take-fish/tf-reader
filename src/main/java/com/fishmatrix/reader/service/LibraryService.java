package com.fishmatrix.reader.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.storage.FileStorage;
import com.fishmatrix.common.storage.StoredFile;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookPage;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.BookUpdateRequest;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.model.ShareLink;
import com.fishmatrix.reader.parser.ChapterParser;
import com.fishmatrix.reader.parser.ChapterParser.ParsedChapter;
import com.fishmatrix.reader.policy.BookAccessPolicy;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.repository.CategoryRepository;
import com.fishmatrix.reader.util.Texts;
import com.fishmatrix.reader.util.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    public static final String SCOPE_PUBLIC = "PUBLIC";
    public static final String SCOPE_PRIVATE = "PRIVATE";
    public static final String STATUS_VISIBLE = "VISIBLE";
    public static final String STATUS_HIDDEN = "HIDDEN";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PAGE_SIZE = 100;
    /** 页码上限：防止 (page * size) 溢出 int，也防止有人从第 200 万页开始扫库。 */
    private static final int MAX_PAGE = 10_000;

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final ChapterParser chapterParser;
    private final FileStorage storage;
    private final BookAccessPolicy accessPolicy;
    private final long maxImportBytes;

    public LibraryService(
            BookRepository bookRepository,
            CategoryRepository categoryRepository,
            ChapterParser chapterParser,
            FileStorage storage,
            BookAccessPolicy accessPolicy,
            @Value("${app.reader.max-import-bytes:8388608}") long maxImportBytes) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.chapterParser = chapterParser;
        this.storage = storage;
        this.accessPolicy = accessPolicy;
        this.maxImportBytes = maxImportBytes;
    }

    public BookPage listPublic(Long categoryId, String keyword, int page, int size,
            CurrentUser viewer) {
        int safePage = Math.min(MAX_PAGE, Math.max(0, page));
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        long offset = (long) safePage * safeSize;
        List<BookSummary> books = bookRepository
                .findPublic(categoryId, keyword, offset, safeSize)
                .stream().map(record -> summary(record, viewer)).toList();
        return new BookPage(books,
                bookRepository.countPublic(categoryId, keyword), safePage, safeSize);
    }

    public List<BookSummary> listMine(CurrentUser viewer) {
        return bookRepository.findOwned(viewer.id()).stream()
                .map(record -> summary(record, viewer))
                .toList();
    }

    public List<BookSummary> listShared(CurrentUser viewer) {
        return bookRepository.findShared(viewer.id()).stream()
                .map(record -> summary(record, viewer))
                .toList();
    }

    public BookDetail getBook(long bookId, CurrentUser viewer) {
        BookRecord book = requireReadable(bookId, viewer);
        List<ChapterSummary> chapters = bookRepository.findChapters(bookId);
        return new BookDetail(summary(book, viewer), chapters);
    }

    public ChapterContent getChapter(long bookId, int chapterIndex, CurrentUser viewer) {
        BookRecord book = requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        return bookRepository.findChapter(bookId, chapterIndex)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
    }

    @Transactional
    public BookSummary importBook(String scope, String title, String author, Long categoryId,
            MultipartFile file, CurrentUser viewer) {
        String normalizedScope = normalizeScope(scope);
        if (SCOPE_PUBLIC.equals(normalizedScope)) {
            requirePermission(viewer, "reader.public.upload");
        } else {
            requirePermission(viewer, "reader.private.manage");
        }
        if (categoryId != null) {
            requirePermission(viewer, "reader.category.manage");
            requireCategory(categoryId);
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要导入的 txt 文件");
        }
        if (file.getSize() > maxImportBytes) {
            throw ApiException.badRequest("文件大小超过限制");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw ApiException.badRequest("仅支持导入 txt 文件");
        }

        String text;
        StoredFile stored;
        try {
            text = Texts.decode(file.getBytes());
            try (InputStream input = file.getInputStream()) {
                stored = storage.store(input, filename, file.getContentType(),
                        "reader/books/" + normalizedScope.toLowerCase(Locale.ROOT), maxImportBytes);
            }
        } catch (IOException exception) {
            throw ApiException.badRequest("文件读取失败");
        }
        List<ParsedChapter> chapters = chapterParser.parse(text);
        if (chapters.isEmpty()) {
            storage.delete(stored.storageKey());
            throw ApiException.badRequest("未解析到有效章节");
        }

        String fallbackTitle = stripExtension(filename);
        String cleanTitle = Validation.truncate(
                title == null || title.isBlank() ? fallbackTitle : title, 255);
        if (cleanTitle.isEmpty()) {
            cleanTitle = "未命名书籍";
        }
        String cleanAuthor = Validation.truncate(author, 128);
        String description = Validation.truncate(
                "导入文本 · " + chapters.size() + " 章 · " + Texts.wordCount(text) + " 字", 1024);
        Instant now = Instant.now();
        long bookId;
        try {
            bookId = bookRepository.insertBook(
                    cleanTitle,
                    cleanAuthor,
                    description,
                    chapters.size(),
                    normalizedScope,
                    viewer.id(),
                    Validation.truncate(viewer.nickname(), 32),
                    categoryId,
                    Validation.truncate(filename, 255),
                    stored.storageKey(),
                    stored.size(),
                    stored.sha256(),
                    now);
            for (int i = 0; i < chapters.size(); i++) {
                ParsedChapter chapter = chapters.get(i);
                String chapterTitle = chapter.title().isBlank()
                        ? "第 " + (i + 1) + " 节" : chapter.title();
                bookRepository.insertChapter(bookId, i,
                        Validation.truncate(chapterTitle, 255),
                        chapter.content(), chapter.wordCount());
            }
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }
        log.info("导入书籍完成",
                kv("event", "book.imported"),
                kv("book_id", bookId),
                kv("scope", normalizedScope),
                kv("chapters", chapters.size()),
                kv("bytes", stored.size()),
                kv("owner_id", viewer.id()));
        return summary(requireRecord(bookId), viewer);
    }

    @Transactional
    public BookSummary update(long bookId, BookUpdateRequest request, CurrentUser viewer) {
        BookRecord book = requireRecord(bookId);
        accessPolicy.requireEditable(book, viewer);
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }

        String title = request.title() == null
                ? book.title()
                : Validation.text(request.title(), 255, "书名", true);
        String author = request.author() == null
                ? book.author() : Validation.truncate(request.author(), 128);
        String description = request.description() == null
                ? book.description() : Validation.truncate(request.description(), 1024);

        Long categoryId = book.categoryId();
        if (request.categoryId() != null && !request.categoryId().equals(book.categoryId())) {
            requirePermission(viewer, "reader.category.manage");
            requireCategory(request.categoryId());
            categoryId = request.categoryId();
        }
        String status = book.status();
        if (request.status() != null && !request.status().equalsIgnoreCase(book.status())) {
            requirePermission(viewer, "reader.content.manage.any");
            status = normalizeStatus(request.status());
        }
        bookRepository.updateMetadata(
                bookId, title, author, description, categoryId, status, Instant.now());
        log.info("更新书籍元信息",
                kv("event", "book.updated"),
                kv("book_id", bookId),
                kv("status", status),
                kv("actor_id", viewer.id()));
        return summary(requireRecord(bookId), viewer);
    }

    @Transactional
    public void delete(long bookId, CurrentUser viewer) {
        BookRecord book = requireRecord(bookId);
        accessPolicy.requireDeletable(book, viewer);
        bookRepository.deleteBook(bookId);
        if (book.storageKey() != null) {
            storage.delete(book.storageKey());
        }
        log.info("删除书籍",
                kv("event", "book.deleted"),
                kv("book_id", bookId),
                kv("scope", book.scope()),
                kv("actor_id", viewer.id()));
    }

    public ShareLink share(long bookId, CurrentUser viewer) {
        BookRecord book = requirePrivateOwner(bookId, viewer);
        if (book.shareToken() != null && !book.shareToken().isBlank()) {
            return new ShareLink(book.shareToken());
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        bookRepository.updateShareToken(bookId, token, Instant.now());
        return new ShareLink(token);
    }

    public BookSummary previewByShareToken(String shareToken, CurrentUser viewer) {
        BookRecord book = bookRepository.findByShareToken(shareToken)
                .filter(record -> SCOPE_PRIVATE.equals(record.scope()))
                .orElseThrow(() -> new NotFoundException("分享链接无效"));
        return summary(book, viewer);
    }

    public BookRecord requireReadable(long bookId, CurrentUser viewer) {
        BookRecord book = requireRecord(bookId);
        accessPolicy.requireReadable(book, viewer);
        return book;
    }

    public BookRecord requirePrivateOwner(long bookId, CurrentUser viewer) {
        BookRecord book = requireRecord(bookId);
        if (!book.isPrivate() || !accessPolicy.isOwner(book, viewer)) {
            throw new NotFoundException("书籍不存在");
        }
        return book;
    }

    public BookRecord requireRecord(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("书籍不存在"));
    }

    public BookSummary summary(BookRecord book, CurrentUser viewer) {
        boolean owner = accessPolicy.isOwner(book, viewer);
        boolean editable = accessPolicy.canModify(book, viewer);
        return new BookSummary(
                book.id(),
                book.title(),
                book.author(),
                book.description(),
                book.chapterCount(),
                book.scope(),
                book.ownerId(),
                book.ownerNickname(),
                book.categoryId(),
                book.categoryName(),
                book.status(),
                owner,
                editable,
                editable,
                book.createdAt(),
                book.updatedAt());
    }

    private void requireCategory(long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.badRequest("分类不存在"));
    }

    private static void requirePermission(CurrentUser viewer, String permission) {
        if (!viewer.hasPermission(permission)) {
            throw ApiException.forbidden("没有执行这个操作的权限");
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return SCOPE_PRIVATE;
        String value = scope.strip().toUpperCase(Locale.ROOT);
        if (!SCOPE_PUBLIC.equals(value) && !SCOPE_PRIVATE.equals(value)) {
            throw ApiException.badRequest("书库类型不合法");
        }
        return value;
    }

    private static String normalizeStatus(String status) {
        String value = status.strip().toUpperCase(Locale.ROOT);
        if (!STATUS_VISIBLE.equals(value) && !STATUS_HIDDEN.equals(value)) {
            throw ApiException.badRequest("书籍状态不合法");
        }
        return value;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
