package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.entity.ReaderAnnotation;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderChapter;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.mapper.ReaderAnnotationMapper;
import com.fishmatrix.reader.mapper.ReaderChapterMapper;
import com.fishmatrix.reader.model.Annotation;
import com.fishmatrix.reader.model.AnnotationRequest;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.util.Validation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnnotationService {

    private static final int MAX_BATCH = 200;

    private final LibraryService libraryService;
    private final ReaderChapterMapper chapterMapper;
    private final ReaderAnnotationMapper annotationMapper;

    public AnnotationService(
            LibraryService libraryService,
            ReaderChapterMapper chapterMapper,
            ReaderAnnotationMapper annotationMapper) {
        this.libraryService = libraryService;
        this.chapterMapper = chapterMapper;
        this.annotationMapper = annotationMapper;
    }

    public List<Annotation> listBookAnnotations(long bookId, int chapterIndex, long afterId,
            int limit, CurrentUser viewer) {
        ReaderBook book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCountOrZero());
        long cursor = Math.max(0, afterId);
        int batch = Math.min(MAX_BATCH, Math.max(1, limit));
        // 游标 + 固定上限，用 last 拼 LIMIT（batch 已被钳制在 1..200，无注入风险）
        return annotationMapper.selectList(Wrappers.<ReaderAnnotation>lambdaQuery()
                        .eq(ReaderAnnotation::getBookId, bookId)
                        .eq(ReaderAnnotation::getChapterIndex, chapterIndex)
                        .gt(ReaderAnnotation::getId, cursor)
                        .orderByAsc(ReaderAnnotation::getId)
                        .last("LIMIT " + batch))
                .stream().map(AnnotationService::toView).toList();
    }

    public Annotation addAnnotation(long bookId, int chapterIndex, AnnotationRequest request,
            CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        ReaderBook book = libraryService.requireReadable(bookId, viewer);
        Validation.requireChapterIndex(chapterIndex, book.chapterCountOrZero());
        ReaderChapter chapter = chapterMapper.selectList(Wrappers.<ReaderChapter>lambdaQuery()
                        .eq(ReaderChapter::getBookId, bookId)
                        .eq(ReaderChapter::getChapterIndex, chapterIndex))
                .stream().findFirst()
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        ChapterContent content = LibraryService.toContent(chapter);
        if (request.paragraphIndex() < 0 || request.paragraphIndex() >= content.paragraphCount()) {
            throw ApiException.badRequest("段落索引不合法");
        }

        ReaderAnnotation entity = new ReaderAnnotation();
        entity.setBookId(bookId);
        entity.setChapterIndex(chapterIndex);
        entity.setParagraphIndex(request.paragraphIndex());
        entity.setUserId(viewer.id());
        entity.setNickname(Validation.truncate(viewer.nickname(), 32));
        entity.setContent(Validation.text(request.content(), 500, "评审意见", true));
        annotationMapper.insert(entity);
        return toView(entity);
    }

    private static Annotation toView(ReaderAnnotation entity) {
        return new Annotation(
                entity.getId(),
                entity.getParagraphIndex(),
                // 对外 DTO 的 userId 仍是字符串，这里给出数字字符串
                String.valueOf(entity.getUserId()),
                entity.getNickname(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
