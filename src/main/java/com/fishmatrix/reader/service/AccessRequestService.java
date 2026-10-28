package com.fishmatrix.reader.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.domain.AccessRequestStatus;
import com.fishmatrix.reader.entity.ReaderAccessRequest;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.error.ReaderErrorCodes;
import com.fishmatrix.reader.mapper.ReaderAccessRequestMapper;
import com.fishmatrix.reader.mapper.ReaderBookMapper;
import com.fishmatrix.reader.model.AccessRequestCreate;
import com.fishmatrix.reader.model.AccessRequestView;
import com.fishmatrix.reader.util.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私有书籍的访问申请。
 *
 * <p>申请记录本身走 {@link ReaderAccessRequestMapper}；列表需要书名和书主昵称，
 * 采用"先查申请、再按 book_id 批量补书"的方式，固定两次查询，不逐行 JOIN。
 * 状态机：PENDING → APPROVED / REJECTED / CANCELLED，APPROVED → REVOKED。</p>
 */
@Service
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    private static final Duration REJECT_COOLDOWN = Duration.ofHours(24);

    private final ReaderAccessRequestMapper accessMapper;
    private final ReaderBookMapper bookMapper;
    private final LibraryService libraryService;

    public AccessRequestService(
            ReaderAccessRequestMapper accessMapper,
            ReaderBookMapper bookMapper,
            LibraryService libraryService) {
        this.accessMapper = accessMapper;
        this.bookMapper = bookMapper;
        this.libraryService = libraryService;
    }

    @Transactional
    public AccessRequestView request(long bookId, AccessRequestCreate create, CurrentUser viewer) {
        ReaderBook book = Optional.ofNullable(bookMapper.selectByIdWithCategory(bookId))
                .filter(ReaderBook::isPrivate)
                .orElseThrow(() -> ApiException.notFound("书籍不存在"));
        if (book.isOwnedBy(viewer.id())) {
            throw ApiException.badRequest("不能申请访问自己的书籍");
        }
        if (!libraryService.shareTokenValid(book)) {
            throw ApiException.forbidden("分享链接已失效");
        }
        if (create == null || create.shareToken() == null || create.shareToken().isBlank()
                || !create.shareToken().equals(book.getShareToken())) {
            throw ApiException.forbidden("请通过有效的分享链接发起申请");
        }
        String message = Validation.text(create.message(), 255, "申请说明", false);
        Instant now = Instant.now();

        Optional<ReaderAccessRequest> existing = findByBookAndRequester(bookId, viewer.id());
        if (existing.isPresent()) {
            ReaderAccessRequest current = existing.get();
            switch (current.getStatus()) {
                case APPROVED, PENDING -> {
                    return toView(current, book);
                }
                case REJECTED -> {
                    if (current.getDecidedAt() != null
                            && current.getDecidedAt().plus(REJECT_COOLDOWN).isAfter(now)) {
                        throw ApiException.tooManyRequests(
                                ReaderErrorCodes.ACCESS_COOLDOWN,
                                "申请被拒绝后 24 小时内不能重复申请");
                    }
                }
                default -> {
                    // CANCELLED / REVOKED 允许重新申请
                }
            }
        }

        ReaderAccessRequest entity = existing.orElseGet(ReaderAccessRequest::new);
        entity.setBookId(bookId);
        entity.setOwnerId(book.getOwnerId());
        entity.setRequesterId(viewer.id());
        entity.setRequesterNickname(Validation.truncate(viewer.nickname(), 32));
        entity.setStatus(AccessRequestStatus.PENDING);
        entity.setMessage(message);
        entity.setDecisionMessage("");
        entity.setDecidedAt(null);
        entity.setCreatedAt(now);
        if (entity.getId() == null) {
            accessMapper.insert(entity);
        } else {
            accessMapper.updateById(entity);
        }

        log.info("发起访问申请",
                kv("event", "access.requested"),
                kv("request_id", entity.getId()),
                kv("book_id", bookId),
                kv("owner_id", book.getOwnerId()),
                kv("requester_id", viewer.id()));
        return toView(require(entity.getId()), book);
    }

    public List<AccessRequestView> mine(CurrentUser viewer) {
        List<ReaderAccessRequest> rows = accessMapper.selectList(
                Wrappers.<ReaderAccessRequest>lambdaQuery()
                        .eq(ReaderAccessRequest::getRequesterId, viewer.id())
                        .orderByDesc(ReaderAccessRequest::getUpdatedAt)
                        .orderByDesc(ReaderAccessRequest::getId));
        return toViews(rows);
    }

    public List<AccessRequestView> received(CurrentUser viewer, AccessRequestStatus status) {
        List<ReaderAccessRequest> rows = accessMapper.selectList(
                Wrappers.<ReaderAccessRequest>lambdaQuery()
                        .eq(ReaderAccessRequest::getOwnerId, viewer.id())
                        .eq(status != null, ReaderAccessRequest::getStatus, status)
                        .orderByDesc(ReaderAccessRequest::getUpdatedAt)
                        .orderByDesc(ReaderAccessRequest::getId));
        return toViews(rows);
    }

    @Transactional
    public AccessRequestView approve(long id, CurrentUser viewer) {
        ReaderAccessRequest request = requireOwnerPending(id, viewer);
        decide(request, AccessRequestStatus.APPROVED, "");
        log.info("通过访问申请",
                kv("event", "access.approved"),
                kv("request_id", id),
                kv("book_id", request.getBookId()),
                kv("requester_id", request.getRequesterId()),
                kv("actor_id", viewer.id()));
        return toView(request, bookMapper.selectByIdWithCategory(request.getBookId()));
    }

    @Transactional
    public AccessRequestView reject(long id, String reason, CurrentUser viewer) {
        ReaderAccessRequest request = requireOwnerPending(id, viewer);
        decide(request, AccessRequestStatus.REJECTED,
                Validation.text(reason, 255, "拒绝理由", false));
        log.info("拒绝访问申请",
                kv("event", "access.rejected"),
                kv("request_id", id),
                kv("book_id", request.getBookId()),
                kv("requester_id", request.getRequesterId()),
                kv("actor_id", viewer.id()));
        return toView(request, bookMapper.selectByIdWithCategory(request.getBookId()));
    }

    @Transactional
    public AccessRequestView revoke(long id, CurrentUser viewer) {
        ReaderAccessRequest request = requireOwnedBy(id, viewer);
        if (!request.getStatus().isApproved()) {
            throw ApiException.conflict(ReaderErrorCodes.ACCESS_INVALID_STATE,
                    "只有已通过的申请才能撤销");
        }
        decide(request, AccessRequestStatus.REVOKED, "");
        log.info("收回访问授权",
                kv("event", "access.revoked"),
                kv("request_id", id),
                kv("book_id", request.getBookId()),
                kv("requester_id", request.getRequesterId()),
                kv("actor_id", viewer.id()));
        return toView(request, bookMapper.selectByIdWithCategory(request.getBookId()));
    }

    @Transactional
    public AccessRequestView cancel(long id, CurrentUser viewer) {
        ReaderAccessRequest request = require(id);
        if (!Objects.equals(request.getRequesterId(), viewer.id())) {
            throw ApiException.notFound("申请不存在");
        }
        if (!request.getStatus().isPending()) {
            throw ApiException.conflict(ReaderErrorCodes.ACCESS_INVALID_STATE,
                    "只有待审批的申请才能取消");
        }
        decide(request, AccessRequestStatus.CANCELLED, "");
        log.info("取消访问申请",
                kv("event", "access.cancelled"),
                kv("request_id", id),
                kv("book_id", request.getBookId()),
                kv("actor_id", viewer.id()));
        return toView(request, bookMapper.selectByIdWithCategory(request.getBookId()));
    }

    private Optional<ReaderAccessRequest> findByBookAndRequester(long bookId, long requesterId) {
        return accessMapper.selectList(Wrappers.<ReaderAccessRequest>lambdaQuery()
                        .eq(ReaderAccessRequest::getBookId, bookId)
                        .eq(ReaderAccessRequest::getRequesterId, requesterId))
                .stream().findFirst();
    }

    private ReaderAccessRequest require(long id) {
        ReaderAccessRequest entity = accessMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("申请不存在");
        }
        return entity;
    }

    private ReaderAccessRequest requireOwnedBy(long id, CurrentUser viewer) {
        ReaderAccessRequest entity = require(id);
        if (!Objects.equals(entity.getOwnerId(), viewer.id())) {
            throw ApiException.notFound("申请不存在");
        }
        return entity;
    }

    private ReaderAccessRequest requireOwnerPending(long id, CurrentUser viewer) {
        ReaderAccessRequest entity = requireOwnedBy(id, viewer);
        if (!entity.getStatus().isPending()) {
            throw ApiException.conflict(ReaderErrorCodes.ACCESS_INVALID_STATE, "申请已处理");
        }
        return entity;
    }

    private void decide(ReaderAccessRequest entity, AccessRequestStatus status, String message) {
        entity.setStatus(status);
        entity.setDecisionMessage(message == null ? "" : message);
        entity.setDecidedAt(Instant.now());
        accessMapper.updateById(entity);
    }

    /** 列表用的批量补书：一次查出所有相关书籍，避免逐行查询。 */
    private List<AccessRequestView> toViews(List<ReaderAccessRequest> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> bookIds = rows.stream()
                .map(ReaderAccessRequest::getBookId).distinct().toList();
        Map<Long, ReaderBook> books = bookMapper.selectByIds(bookIds).stream()
                .collect(Collectors.toMap(ReaderBook::getId, book -> book));
        return rows.stream().map(row -> toView(row, books.get(row.getBookId()))).toList();
    }

    private static AccessRequestView toView(ReaderAccessRequest entity, ReaderBook book) {
        return new AccessRequestView(
                entity.getId(),
                entity.getBookId(),
                book == null ? "" : book.getTitle(),
                entity.getOwnerId(),
                book == null ? "" : book.getOwnerNickname(),
                entity.getRequesterId(),
                entity.getRequesterNickname(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getDecisionMessage(),
                entity.getCreatedAt(),
                entity.getDecidedAt());
    }
}
