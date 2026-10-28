package com.fishmatrix.reader.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fishmatrix.common.messaging.DomainEvent;
import com.fishmatrix.common.messaging.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AccessEventPublisherTest {

    private final EventPublisher publisher = mock(EventPublisher.class);
    private final AccessEventPublisher events = new AccessEventPublisher(publisher);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("新申请事件使用申请 ID 作为 key")
    void requestedEvent() {
        events.requested(88L, 7L, 21L, 22L);

        DomainEvent event = capture();
        assertThat(event.type()).isEqualTo(AccessEventPublisher.TYPE_REQUESTED);
        assertThat(event.source()).isEqualTo(AccessEventPublisher.SOURCE);
        assertThat(event.key()).isEqualTo("88");
        assertThat(event.payload())
                .containsEntry("bookId", 7L)
                .containsEntry("ownerId", 21L)
                .containsEntry("requesterId", 22L)
                .containsEntry("status", "PENDING");
    }

    @Test
    @DisplayName("审批事件共用一种类型，状态放在 payload")
    void decidedEvent() {
        events.decided(88L, 7L, 22L, 21L, "APPROVED");

        DomainEvent event = capture();
        assertThat(event.type()).isEqualTo(AccessEventPublisher.TYPE_DECIDED);
        assertThat(event.key()).isEqualTo("88");
        assertThat(event.payload()).containsEntry("actorId", 21L).containsEntry("status", "APPROVED");
    }

    @Test
    @DisplayName("发布失败只记日志，不向调用方抛异常")
    void publishFailureIsSwallowed() {
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());

        assertThatCode(() -> events.requested(88L, 7L, 21L, 22L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事务中等到提交后才发布，避免回滚后仍对外发事件")
    void waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        events.requested(88L, 7L, 21L, 22L);
        verify(publisher, never()).publish(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(publisher).publish(any());
    }

    private DomainEvent capture() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }
}
