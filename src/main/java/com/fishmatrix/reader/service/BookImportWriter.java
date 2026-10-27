package com.fishmatrix.reader.service;

import com.fishmatrix.reader.domain.BookScope;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.ChapterRow;
import java.time.Instant;
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

    private final BookRepository bookRepository;
    private final ExperienceService experienceService;

    public BookImportWriter(BookRepository bookRepository, ExperienceService experienceService) {
        this.bookRepository = bookRepository;
        this.experienceService = experienceService;
    }

    /** 一个短事务：写书籍行、批量写章节、给上传者加经验。任何一步失败整体回滚。 */
    @Transactional
    public long persist(ImportDraft draft) {
        long bookId = bookRepository.insertBook(
                draft.title(),
                draft.author(),
                draft.description(),
                draft.chapters().size(),
                draft.scope(),
                draft.ownerId(),
                draft.ownerNickname(),
                draft.categoryId(),
                draft.sourceFilename(),
                draft.storageKey(),
                draft.fileSize(),
                draft.sha256(),
                Instant.now());
        bookRepository.insertChapters(bookId, draft.chapters());
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
            List<ChapterRow> chapters) {
    }
}
