package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.reader.entity.ReaderAnnotation;
import com.fishmatrix.reader.entity.ReaderBookmark;
import com.fishmatrix.reader.entity.ReaderChapter;
import com.fishmatrix.reader.entity.ReaderProgress;
import com.fishmatrix.reader.entity.ReaderShelfBook;
import com.fishmatrix.reader.mapper.ReaderAccessRequestMapper;
import com.fishmatrix.reader.mapper.ReaderAnnotationMapper;
import com.fishmatrix.reader.mapper.ReaderBookMapper;
import com.fishmatrix.reader.mapper.ReaderBookmarkMapper;
import com.fishmatrix.reader.mapper.ReaderChapterMapper;
import com.fishmatrix.reader.mapper.ReaderProgressMapper;
import com.fishmatrix.reader.mapper.ReaderShelfBookMapper;
import com.fishmatrix.reader.entity.ReaderAccessRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除书籍时的级联清理。
 *
 * <p>数据库侧没有为这些表建外键（V5 会补），所以删除顺序必须显式维护在代码里，
 * 集中放在一处而不是散落在业务方法中，避免以后加表时漏掉某一个。</p>
 */
@Service
public class BookRemovalService {

    private final ReaderBookMapper bookMapper;
    private final ReaderChapterMapper chapterMapper;
    private final ReaderShelfBookMapper shelfBookMapper;
    private final ReaderAccessRequestMapper accessMapper;
    private final ReaderAnnotationMapper annotationMapper;
    private final ReaderBookmarkMapper bookmarkMapper;
    private final ReaderProgressMapper progressMapper;

    public BookRemovalService(
            ReaderBookMapper bookMapper,
            ReaderChapterMapper chapterMapper,
            ReaderShelfBookMapper shelfBookMapper,
            ReaderAccessRequestMapper accessMapper,
            ReaderAnnotationMapper annotationMapper,
            ReaderBookmarkMapper bookmarkMapper,
            ReaderProgressMapper progressMapper) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.shelfBookMapper = shelfBookMapper;
        this.accessMapper = accessMapper;
        this.annotationMapper = annotationMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.progressMapper = progressMapper;
    }

    @Transactional
    public void deleteCascade(long bookId) {
        shelfBookMapper.delete(Wrappers.<ReaderShelfBook>lambdaQuery()
                .eq(ReaderShelfBook::getBookId, bookId));
        accessMapper.delete(Wrappers.<ReaderAccessRequest>lambdaQuery()
                .eq(ReaderAccessRequest::getBookId, bookId));
        annotationMapper.delete(Wrappers.<ReaderAnnotation>lambdaQuery()
                .eq(ReaderAnnotation::getBookId, bookId));
        bookmarkMapper.delete(Wrappers.<ReaderBookmark>lambdaQuery()
                .eq(ReaderBookmark::getBookId, bookId));
        progressMapper.delete(Wrappers.<ReaderProgress>lambdaQuery()
                .eq(ReaderProgress::getBookId, bookId));
        chapterMapper.delete(Wrappers.<ReaderChapter>lambdaQuery()
                .eq(ReaderChapter::getBookId, bookId));
        bookMapper.deleteById(bookId);
    }
}
