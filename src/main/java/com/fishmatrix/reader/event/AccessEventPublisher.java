package com.fishmatrix.reader.event;

import com.fishmatrix.common.messaging.DomainEvent;
import com.fishmatrix.common.messaging.EventPublisher;
import com.fishmatrix.common.trace.TraceIds;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 私有书籍访问申请的旁路事件。
 *
 * <p>访问申请不属于核心交易链路：事件只用于后续的通知、审计与统计。
 * 因此这里刻意做成尽力而为 —— 发布失败只记 WARN，不让申请 / 审批失败；
 * 有事务时注册 {@code afterCommit}，避免事务回滚后仍对外发出事件。</p>
 *
 * <p>事件契约：{@code READER_ACCESS_REQUESTED}（新申请）与
 * {@code READER_ACCESS_DECIDED}（通过 / 拒绝 / 撤销 / 取消，状态放 payload.status），
 * key 统一使用申请 ID。</p>
 */
@Component
@Slf4j
public class AccessEventPublisher {

    public static final String TYPE_REQUESTED = "READER_ACCESS_REQUESTED";
    public static final String TYPE_DECIDED = "READER_ACCESS_DECIDED";
    /** 事件来源固定为服务名，不随部署环境变化。 */
    static final String SOURCE = "tf-reader";

    private final EventPublisher publisher;

    public AccessEventPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    /** 新申请：requester 发起，owner 待处理。 */
    public void requested(long requestId, long bookId, long ownerId, long requesterId) {
        publish(
                TYPE_REQUESTED,
                requestId,
                Map.of(
                        "requestId", requestId,
                        "bookId", bookId,
                        "ownerId", ownerId,
                        "requesterId", requesterId,
                        "status", "PENDING"));
    }

    /** 审批结果：approve / reject / revoke / cancel 共用一种事件，用 status 区分。 */
    public void decided(long requestId, long bookId, long requesterId, long actorId, String status) {
        publish(
                TYPE_DECIDED,
                requestId,
                Map.of(
                        "requestId", requestId,
                        "bookId", bookId,
                        "requesterId", requesterId,
                        "actorId", actorId,
                        "status", status));
    }

    private void publish(String type, long requestId, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(type, requestId, payload);
                }
            });
            return;
        }
        send(type, requestId, payload);
    }

    private void send(String type, long requestId, Map<String, Object> payload) {
        try {
            publisher.publish(
                    DomainEvent.of(type, SOURCE, TraceIds.currentTraceId(), String.valueOf(requestId), payload));
        } catch (RuntimeException e) {
            log.warn("访问申请事件发布失败（不影响主流程） type={} requestId={}", type, requestId, e);
        }
    }
}
