package com.fishmatrix.reader.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.domain.AccessRequestStatus;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.error.ReaderErrorCodes;
import com.fishmatrix.reader.model.AccessRequestCreate;
import com.fishmatrix.reader.model.AccessRequestView;
import com.fishmatrix.reader.repository.AccessRequestRepository;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.util.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    private static final Duration REJECT_COOLDOWN = Duration.ofHours(24);

    private final AccessRequestRepository repository;
    private final BookRepository bookRepository;
    private final LibraryService libraryService;

    public AccessRequestService(AccessRequestRepository repository,
            BookRepository bookRepository, LibraryService libraryService) {
        this.repository = repository;
        this.bookRepository = bookRepository;
        this.libraryService = libraryService;
    }

    @Transactional
    public AccessRequestView request(long bookId, AccessRequestCreate create, CurrentUser viewer) {
        BookRecord book = bookRepository.findById(bookId)
                .filter(BookRecord::isPrivate)
                .orElseThrow(() -> ApiException.notFound("书籍不存在"));
        if (book.isOwnedBy(viewer.id())) {
            throw ApiException.badRequest("不能申请访问自己的书籍");
        }
        if (create == null || create.shareToken() == null || create.shareToken().isBlank()
                || !create.shareToken().equals(book.shareToken())) {
            throw ApiException.forbidden("请通过有效的分享链接发起申请");
        }
        String message = Validation.text(create.message(), 255, "申请说明", false);
        Instant now = Instant.now();
        Optional<AccessRequestView> existing =
                repository.findByBookAndRequester(bookId, viewer.id());
        if (existing.isPresent()) {
            AccessRequestView current = existing.get();
            switch (current.status()) {
                case APPROVED, PENDING -> {
                    return current;
                }
                case REJECTED -> {
                    if (current.decidedAt() != null
                            && current.decidedAt().plus(REJECT_COOLDOWN).isAfter(now)) {
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
        long id = repository.upsertPending(bookId, book.ownerId(), viewer.id(),
                Validation.truncate(viewer.nickname(), 32), message, now);
        log.info("发起访问申请",
                kv("event", "access.requested"),
                kv("request_id", id),
                kv("book_id", bookId),
                kv("owner_id", book.ownerId()),
                kv("requester_id", viewer.id()));
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("申请不存在"));
    }

    public List<AccessRequestView> mine(CurrentUser viewer) {
        return repository.findByRequester(viewer.id());
    }

    public List<AccessRequestView> received(CurrentUser viewer, AccessRequestStatus status) {
        return repository.findByOwner(viewer.id(), status);
    }

    @Transactional
    public AccessRequestView approve(long id, CurrentUser viewer) {
        AccessRequestView request = requireOwnerPending(id, viewer);
        repository.decide(request.id(), AccessRequestStatus.APPROVED, "", Instant.now());
        log.info("通过访问申请",
                kv("event", "access.approved"),
                kv("request_id", id),
                kv("book_id", request.bookId()),
                kv("requester_id", request.requesterId()),
                kv("actor_id", viewer.id()));
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("申请不存在"));
    }

    @Transactional
    public AccessRequestView reject(long id, String reason, CurrentUser viewer) {
        AccessRequestView request = requireOwnerPending(id, viewer);
        repository.decide(request.id(), AccessRequestStatus.REJECTED,
                Validation.text(reason, 255, "拒绝理由", false), Instant.now());
        log.info("拒绝访问申请",
                kv("event", "access.rejected"),
                kv("request_id", id),
                kv("book_id", request.bookId()),
                kv("requester_id", request.requesterId()),
                kv("actor_id", viewer.id()));
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("申请不存在"));
    }

    @Transactional
    public AccessRequestView revoke(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.ownerId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!request.status().isApproved()) {
            throw ApiException.conflict(ReaderErrorCodes.ACCESS_INVALID_STATE,
                    "只有已通过的申请才能撤销");
        }
        repository.decide(id, AccessRequestStatus.REVOKED, "", Instant.now());
        log.info("收回访问授权",
                kv("event", "access.revoked"),
                kv("request_id", id),
                kv("book_id", request.bookId()),
                kv("requester_id", request.requesterId()),
                kv("actor_id", viewer.id()));
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("申请不存在"));
    }

    @Transactional
    public AccessRequestView cancel(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.requesterId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!request.status().isPending()) {
            throw ApiException.conflict(ReaderErrorCodes.ACCESS_INVALID_STATE,
                    "只有待审批的申请才能取消");
        }
        repository.decide(id, AccessRequestStatus.CANCELLED, "", Instant.now());
        log.info("取消访问申请",
                kv("event", "access.cancelled"),
                kv("request_id", id),
                kv("book_id", request.bookId()),
                kv("actor_id", viewer.id()));
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("申请不存在"));
    }

    private AccessRequestView requireOwnerPending(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.ownerId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!request.status().isPending()) {
            throw ApiException.conflict("INVALID_STATE", "申请已处理");
        }
        return request;
    }
}
