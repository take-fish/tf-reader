package com.fishmatrix.reader.service;

import com.fishmatrix.reader.model.Annotation;
import com.fishmatrix.reader.model.AnnotationRequest;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.repository.AnnotationRepository;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnnotationService {

    private static final int MAX_BATCH = 200;

    private final BookRepository bookRepository;
    private final AnnotationRepository annotationRepository;

    public AnnotationService(
            BookRepository bookRepository,
            AnnotationRepository annotationRepository) {
        this.bookRepository = bookRepository;
        this.annotationRepository = annotationRepository;
    }

    public List<Annotation> listBookAnnotations(
            long bookId,
            int chapterIndex,
            long afterId,
            int limit) {
        BookSummary book = requireBook(bookId);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        long cursor = Math.max(0, afterId);
        int batch = Math.min(MAX_BATCH, Math.max(1, limit));
        return annotationRepository.findAfter(bookId, chapterIndex, cursor, batch);
    }

    public Annotation addAnnotation(
            long bookId,
            int chapterIndex,
            AnnotationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Validation.requireUserId(request.userId());
        BookSummary book = requireBook(bookId);
        Validation.requireChapterIndex(chapterIndex, book.chapterCount());
        ChapterContent chapter = bookRepository.findChapter(bookId, chapterIndex)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        if (request.paragraphIndex() < 0 || request.paragraphIndex() >= chapter.paragraphCount()) {
            throw new IllegalArgumentException("段落索引不合法");
        }
        String nickname = Validation.text(request.nickname(), 32, "昵称", true);
        String content = Validation.text(request.content(), 500, "评审意见", true);
        return annotationRepository.insert(
                bookId,
                chapterIndex,
                request.paragraphIndex(),
                request.userId(),
                nickname,
                content,
                Instant.now());
    }

    private BookSummary requireBook(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("书籍不存在"));
    }
}
