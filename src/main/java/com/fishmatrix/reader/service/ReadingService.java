package com.fishmatrix.reader.service;

import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.BookmarkRequest;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.model.ReadingProgress;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.ReadingRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ReadingService {

    private final BookRepository bookRepository;
    private final ReadingRepository readingRepository;

    public ReadingService(BookRepository bookRepository, ReadingRepository readingRepository) {
        this.bookRepository = bookRepository;
        this.readingRepository = readingRepository;
    }

    public Optional<ReadingProgress> getProgress(String userId, long bookId) {
        Validation.requireUserId(userId);
        requireBook(bookId);
        return readingRepository.findProgress(userId, bookId);
    }

    public void saveProgress(String userId, long bookId, int chapterIndex, double scrollRatio) {
        Validation.requireUserId(userId);
        BookSummary book = requireBook(bookId);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        readingRepository.upsertProgress(
                userId,
                bookId,
                chapterIndex,
                Validation.clampRatio(scrollRatio),
                Instant.now());
    }

    public List<Bookmark> listBookmarks(String userId, long bookId) {
        Validation.requireUserId(userId);
        requireBook(bookId);
        return readingRepository.findBookmarks(userId, bookId);
    }

    public Bookmark addBookmark(long bookId, BookmarkRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Validation.requireUserId(request.userId());
        BookSummary book = requireBook(bookId);
        Validation.requireChapterIndex(request.chapterIndex(), book.chapterCount());
        if (request.paragraphIndex() < 0) {
            throw new IllegalArgumentException("段落索引不合法");
        }
        String excerpt = Validation.text(request.excerpt(), 512, "摘录", false);
        String note = Validation.text(request.note(), 500, "批注", false);

        Optional<Bookmark> existing = readingRepository.findBookmark(
                request.userId(),
                bookId,
                request.chapterIndex(),
                request.paragraphIndex());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return readingRepository.insertBookmark(
                    request.userId(),
                    bookId,
                    request.chapterIndex(),
                    request.paragraphIndex(),
                    excerpt,
                    note.isBlank() ? null : note,
                    Instant.now());
        } catch (DuplicateKeyException e) {
            return readingRepository.findBookmark(
                            request.userId(),
                            bookId,
                            request.chapterIndex(),
                            request.paragraphIndex())
                    .orElseThrow(() -> new NotFoundException("书签不存在"));
        }
    }

    public void deleteBookmark(long bookmarkId, String userId) {
        Validation.requireUserId(userId);
        if (!readingRepository.deleteBookmark(bookmarkId, userId)) {
            throw new NotFoundException("书签不存在");
        }
    }

    private BookSummary requireBook(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("书籍不存在"));
    }
}
