package com.fishmatrix.reader.service;

import com.fishmatrix.reader.domain.BookScope;
import com.fishmatrix.reader.domain.BookStatus;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.mapper.ReaderBookMapper;
import com.fishmatrix.reader.mapper.ReaderChapterMapper;
import com.fishmatrix.reader.model.ChapterRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 书籍导入的落库步骤。
 *
 * <p>单独成 bean 有两个原因：一是把事务范围压到"只写库"，文件落盘与章节解析都留在事务之外，
 * 避免边持有数据库连接边做磁盘 I/O；二是 Spring 的事务靠代理生效，
 * 放在 {@link LibraryService} 内部自调用不会开启事务。</p>
 */
@Service
public class BookImportWriter {

    private final ReaderBookMapper bookMapper;
    private final ReaderChapterMapper chapterMapper;
    private final ExperienceService experienceService;

    public BookImportWriter(
            ReaderBookMapper bookMapper, ReaderChapterMapper chapterMapper, ExperienceService experienceService) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.experienceService = experienceService;
    }

    /** 一个短事务：写书籍行、批量写章节、给上传者加经验。任何一步失败整体回滚。 */
    @Transactional
    public long persist(ImportDraft draft) {
        ReaderBook book = new ReaderBook();
        book.setTitle(draft.title());
        book.setAuthor(draft.author());
        book.setDescription(draft.description());
        book.setChapterCount(draft.chapters().size());
        book.setScope(draft.scope());
        book.setOwnerId(draft.ownerId());
        book.setOwnerNickname(draft.ownerNickname());
        book.setCategoryId(draft.categoryId());
        book.setStatus(BookStatus.VISIBLE);
        book.setSourceFilename(draft.sourceFilename());
        book.setStorageKey(draft.storageKey());
        book.setFileSize(draft.fileSize());
        book.setSha256(draft.sha256());
        bookMapper.insert(book);

        long bookId = book.getId();
        if (!draft.chapters().isEmpty()) {
            chapterMapper.insertChapters(bookId, draft.chapters());
        }
        experienceService.rewardUpload(draft.ownerId());
        return bookId;
    }

    /** 已经解析完成、可以直接落库的导入数据。 */
    public record ImportDraft(
            String title,
            String author,
            String description,
            BookScope scope,
            long ownerId,
            String ownerNickname,
            Long categoryId,
            String sourceFilename,
            String storageKey,
            long fileSize,
            String sha256,
            List<ChapterRow> chapters) {}
}
