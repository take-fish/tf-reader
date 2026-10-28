package com.fishmatrix.reader.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.BookmarkRequest;
import com.fishmatrix.reader.model.ReadingProgress;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.repository.ReadingRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ReadingService {

    private final LibraryService libraryService;
    private final ReadingRepository readingRepository;

    public ReadingService(LibraryService libraryService, ReadingRepository readingRepository) {
        this.libraryService = libraryService;
        this.readingRepository = readingRepository;
    }

    public Optional<ReadingProgress> getProgress(CurrentUser viewer, long bookId) {
        libraryService.requireReadable(bookId, viewer);
        return readingRepository.findProgress(viewer.id(), bookId);
    }

    public void saveProgress(CurrentUser viewer, long bookId, int chapterIndex,
            double scrollRatio) {
        BookRecord book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        readingRepository.upsertProgress(viewer.id(), bookId, chapterIndex,
                Validation.clampRatio(scrollRatio), Instant.now());
    }

    public List<Bookmark> listBookmarks(CurrentUser viewer, long bookId) {
        libraryService.requireReadable(bookId, viewer);
        return readingRepository.findBookmarks(viewer.id(), bookId);
    }

    public Bookmark addBookmark(long bookId, BookmarkRequest request, CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        BookRecord book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(request.chapterIndex(), book.chapterCount());
        if (request.paragraphIndex() < 0) {
            throw ApiException.badRequest("段落索引不合法");
        }
        String excerpt = Validation.text(request.excerpt(), 512, "摘录", false);
        String note = Validation.text(request.note(), 500, "批注", false);
        long userId = viewer.id();

        Optional<Bookmark> existing = readingRepository.findBookmark(
                userId, bookId, request.chapterIndex(), request.paragraphIndex());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return readingRepository.insertBookmark(userId, bookId, request.chapterIndex(),
                    request.paragraphIndex(), excerpt, note.isBlank() ? null : note, Instant.now());
        } catch (DuplicateKeyException exception) {
            return readingRepository.findBookmark(
                            userId, bookId, request.chapterIndex(), request.paragraphIndex())
                    .orElseThrow(() -> new NotFoundException("书签不存在"));
        }
    }

    public void deleteBookmark(long bookmarkId, CurrentUser viewer) {
        if (!readingRepository.deleteBookmark(bookmarkId, viewer.id())) {
            throw new NotFoundException("书签不存在");
        }
    }
}
