package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderBookmark;
import com.fishmatrix.reader.entity.ReaderProgress;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.mapper.ReaderBookmarkMapper;
import com.fishmatrix.reader.mapper.ReaderProgressMapper;
import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.BookmarkRequest;
import com.fishmatrix.reader.model.ReadingProgress;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadingService {

    private final LibraryService libraryService;
    private final ReaderProgressMapper progressMapper;
    private final ReaderBookmarkMapper bookmarkMapper;

    public Optional<ReadingProgress> getProgress(CurrentUser viewer, long bookId) {
        libraryService.requireReadable(bookId, viewer);
        return progressMapper
                .selectList(Wrappers.<ReaderProgress>lambdaQuery()
                        .eq(ReaderProgress::getUserId, viewer.id())
                        .eq(ReaderProgress::getBookId, bookId))
                .stream()
                .findFirst()
                .map(row -> new ReadingProgress(row.getChapterIndex(), row.getScrollRatio(), row.getUpdatedAt()));
    }

    public void saveProgress(CurrentUser viewer, long bookId, int chapterIndex, double scrollRatio) {
        ReaderBook book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCountOrZero());
        ReaderProgress progress = new ReaderProgress();
        progress.setUserId(viewer.id());
        progress.setBookId(bookId);
        progress.setChapterIndex(chapterIndex);
        progress.setScrollRatio(Validation.clampRatio(scrollRatio));
        progress.setUpdatedAt(Instant.now());
        progressMapper.upsert(progress);
    }

    public List<Bookmark> listBookmarks(CurrentUser viewer, long bookId) {
        libraryService.requireReadable(bookId, viewer);
        return bookmarkMapper
                .selectList(Wrappers.<ReaderBookmark>lambdaQuery()
                        .eq(ReaderBookmark::getUserId, viewer.id())
                        .eq(ReaderBookmark::getBookId, bookId)
                        .orderByAsc(ReaderBookmark::getChapterIndex)
                        .orderByAsc(ReaderBookmark::getParagraphIndex)
                        .orderByAsc(ReaderBookmark::getId))
                .stream()
                .map(ReadingService::toView)
                .toList();
    }

    public Bookmark addBookmark(long bookId, BookmarkRequest request, CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        ReaderBook book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(request.chapterIndex(), book.chapterCountOrZero());
        if (request.paragraphIndex() < 0) {
            throw ApiException.badRequest("段落索引不合法");
        }
        String excerpt = Validation.text(request.excerpt(), 512, "摘录", false);
        String note = Validation.text(request.note(), 500, "批注", false);

        Optional<Bookmark> existing =
                findBookmark(viewer.id(), bookId, request.chapterIndex(), request.paragraphIndex());
        if (existing.isPresent()) {
            return existing.get();
        }

        ReaderBookmark entity = new ReaderBookmark();
        entity.setUserId(viewer.id());
        entity.setBookId(bookId);
        entity.setChapterIndex(request.chapterIndex());
        entity.setParagraphIndex(request.paragraphIndex());
        entity.setExcerpt(excerpt);
        entity.setNote(note.isBlank() ? null : note);
        try {
            bookmarkMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 并发下唯一键兜底：同一位置的书签已经存在，取回已有的
            return findBookmark(viewer.id(), bookId, request.chapterIndex(), request.paragraphIndex())
                    .orElseThrow(() -> new NotFoundException("书签不存在"));
        }
        return toView(entity);
    }

    public void deleteBookmark(long bookmarkId, CurrentUser viewer) {
        int removed = bookmarkMapper.delete(Wrappers.<ReaderBookmark>lambdaQuery()
                .eq(ReaderBookmark::getId, bookmarkId)
                .eq(ReaderBookmark::getUserId, viewer.id()));
        if (removed == 0) {
            throw new NotFoundException("书签不存在");
        }
    }

    private Optional<Bookmark> findBookmark(long userId, long bookId, int chapterIndex, int paragraphIndex) {
        return bookmarkMapper
                .selectList(Wrappers.<ReaderBookmark>lambdaQuery()
                        .eq(ReaderBookmark::getUserId, userId)
                        .eq(ReaderBookmark::getBookId, bookId)
                        .eq(ReaderBookmark::getChapterIndex, chapterIndex)
                        .eq(ReaderBookmark::getParagraphIndex, paragraphIndex))
                .stream()
                .findFirst()
                .map(ReadingService::toView);
    }

    private static Bookmark toView(ReaderBookmark entity) {
        return new Bookmark(
                entity.getId(),
                entity.getChapterIndex(),
                entity.getParagraphIndex(),
                entity.getExcerpt(),
                entity.getNote(),
                entity.getCreatedAt());
    }
}
