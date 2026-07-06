package com.loopers.interfaces.api.admin.dlq;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.kafka.DlqReplayer;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.dlq.dto.DlqReplayV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqAdminV1ControllerTest {

    @Mock
    private DlqReplayer dlqReplayer;
    @InjectMocks
    private DlqAdminV1Controller controller;

    private final AdminUser admin = new AdminUser("ops");

    @DisplayName("요청한 토픽으로 재처리를 위임하고 재발행 건수를 응답에 담는다")
    @Test
    void delegatesReplayAndReturnsCount() {
        // given - 토픽 허용 여부 판단은 DlqReplayer 책임이므로 컨트롤러는 위임만 검증한다
        when(dlqReplayer.replay(KafkaTopics.ORDER_EVENTS)).thenReturn(2);

        // when
        ApiResponse<DlqReplayV1Response> response = controller.replay(admin, KafkaTopics.ORDER_EVENTS);

        // then
        verify(dlqReplayer).replay(KafkaTopics.ORDER_EVENTS);
        assertThat(response.data().topic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
        assertThat(response.data().replayed()).isEqualTo(2);
    }
}
