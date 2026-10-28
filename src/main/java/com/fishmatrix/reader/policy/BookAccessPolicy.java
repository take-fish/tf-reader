package com.fishmatrix.reader.policy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.domain.AccessRequestStatus;
import com.fishmatrix.reader.entity.ReaderAccessRequest;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.mapper.ReaderAccessRequestMapper;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 书籍可见性与可管理性的唯一判定入口。
 *
 * <p>这些规则同时决定"能不能读"和"能不能改"。此前 {@code LibraryService} 与
 * {@code ShelfService} 各写了一份，改一处漏一处就是越权入口，因此收敛到这里。</p>
 */
@Component
public class BookAccessPolicy {

    /** 管理任意公共书籍的权限；私有书籍不在此列，私有内容只属于所有者。 */
    private static final String PERMISSION_MANAGE_ANY = "reader.content.manage.any";

    private final ReaderAccessRequestMapper accessMapper;

    public BookAccessPolicy(ReaderAccessRequestMapper accessMapper) {
        this.accessMapper = accessMapper;
    }

    public boolean canRead(ReaderBook book, CurrentUser viewer) {
        if (book.isPrivate()) {
            return isOwner(book, viewer) || hasApprovedAccess(book.getId(), viewer.id());
        }
        return book.isVisible() || canManageAny(viewer);
    }

    /**
     * 批量判定用的重载：{@code approvedBookIds} 由调用方一次性查好，
     * 避免"书单里每本私有书查一次审批记录"造成的 N+1。
     */
    public boolean canRead(ReaderBook book, CurrentUser viewer, Set<Long> approvedBookIds) {
        if (book.isPrivate()) {
            return isOwner(book, viewer) || approvedBookIds.contains(book.getId());
        }
        return book.isVisible() || canManageAny(viewer);
    }

    public boolean canModify(ReaderBook book, CurrentUser viewer) {
        return isOwner(book, viewer) || (book.isPublic() && canManageAny(viewer));
    }

    public boolean canManageAny(CurrentUser viewer) {
        return viewer.hasPermission(PERMISSION_MANAGE_ANY);
    }

    public boolean isOwner(ReaderBook book, CurrentUser viewer) {
        return book.isOwnedBy(viewer.id());
    }

    /** 读不到就当作不存在：私有内容对无权用户返回 404，不暴露存在性。 */
    public void requireReadable(ReaderBook book, CurrentUser viewer) {
        if (!canRead(book, viewer)) {
            throw new NotFoundException("书籍不存在");
        }
    }

    public void requireEditable(ReaderBook book, CurrentUser viewer) {
        requireModifiable(book, viewer, "管理");
    }

    public void requireDeletable(ReaderBook book, CurrentUser viewer) {
        requireModifiable(book, viewer, "删除");
    }

    private void requireModifiable(ReaderBook book, CurrentUser viewer, String action) {
        boolean owner = isOwner(book, viewer);
        if (!book.isPublic() && !owner) {
            // 私有内容对非所有者统一表现为不存在，避免泄露书名等元信息。
            throw new NotFoundException("书籍不存在");
        }
        if (!canModify(book, viewer)) {
            throw ApiException.forbidden("没有" + action + "这本书的权限");
        }
    }

    private boolean hasApprovedAccess(long bookId, long viewerId) {
        Long approved = accessMapper.selectCount(Wrappers.<ReaderAccessRequest>lambdaQuery()
                .eq(ReaderAccessRequest::getBookId, bookId)
                .eq(ReaderAccessRequest::getRequesterId, viewerId)
                .eq(ReaderAccessRequest::getStatus, AccessRequestStatus.APPROVED));
        return approved != null && approved > 0;
    }
}
