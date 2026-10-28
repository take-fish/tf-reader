package com.fishmatrix.reader.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.Annotation;
import com.fishmatrix.reader.model.AnnotationRequest;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.repository.AnnotationRepository;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnnotationService {

    private static final int MAX_BATCH = 200;

    private final LibraryService libraryService;
    private final BookRepository bookRepository;
    private final AnnotationRepository annotationRepository;

    public AnnotationService(
            LibraryService libraryService,
            BookRepository bookRepository,
            AnnotationRepository annotationRepository) {
        this.libraryService = libraryService;
        this.bookRepository = bookRepository;
        this.annotationRepository = annotationRepository;
    }

    public List<Annotation> listBookAnnotations(long bookId, int chapterIndex, long afterId,
            int limit, CurrentUser viewer) {
        BookRecord book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        long cursor = Math.max(0, afterId);
        int batch = Math.min(MAX_BATCH, Math.max(1, limit));
        return annotationRepository.findAfter(bookId, chapterIndex, cursor, batch);
    }

    public Annotation addAnnotation(long bookId, int chapterIndex, AnnotationRequest request,
            CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        BookRecord book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        ChapterContent chapter = bookRepository.findChapter(bookId, chapterIndex)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        if (request.paragraphIndex() < 0 || request.paragraphIndex() >= chapter.paragraphCount()) {
            throw ApiException.badRequest("段落索引不合法");
        }
        String content = Validation.text(request.content(), 500, "评审意见", true);
        return annotationRepository.insert(
                bookId,
                chapterIndex,
                request.paragraphIndex(),
                viewer.id(),
                Validation.truncate(viewer.nickname(), 32),
                content,
                Instant.now());
    }
}
