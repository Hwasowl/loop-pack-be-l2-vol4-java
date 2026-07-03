package com.loopers.interfaces.api.admin.dlq;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.kafka.DlqReplayer;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.dlq.dto.DlqReplayV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqAdminV1ControllerTest {

    @Mock
    private DlqReplayer dlqReplayer;
    @InjectMocks
    private DlqAdminV1Controller controller;

    private final AdminUser admin = new AdminUser("ops");

    @DisplayName("화이트리스트에 없는 토픽으로 재처리를 요청하면 BAD_REQUEST가 발생하고 재처리는 시도되지 않는다")
    @Test
    void rejectsUnknownTopic() {
        // when & then
        assertThatThrownBy(() -> controller.replay(admin, "arbitrary-topic"))
            .isInstanceOfSatisfying(CoreException.class, ex ->
                assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        verify(dlqReplayer, never()).replay(org.mockito.ArgumentMatchers.anyString());
    }

    @DisplayName("허용된 토픽이면 재처리를 위임하고 재발행 건수를 응답에 담는다")
    @Test
    void replaysAllowedTopic() {
        // given
        when(dlqReplayer.replay(KafkaTopics.ORDER_EVENTS)).thenReturn(2);

        // when
        ApiResponse<DlqReplayV1Response> response = controller.replay(admin, KafkaTopics.ORDER_EVENTS);

        // then
        verify(dlqReplayer).replay(KafkaTopics.ORDER_EVENTS);
        assertThat(response.data().topic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
        assertThat(response.data().replayed()).isEqualTo(2);
    }
}
