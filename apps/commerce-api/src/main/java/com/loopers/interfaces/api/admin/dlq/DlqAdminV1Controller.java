package com.loopers.interfaces.api.admin.dlq;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.kafka.DlqReplayer;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.dlq.dto.DlqReplayV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api-admin/v1/dlq")
@RequiredArgsConstructor
@Validated
public class DlqAdminV1Controller implements DlqAdminV1ApiSpec {

    /** 재처리를 허용하는 원본 토픽 화이트리스트 — 임의 토픽으로의 재발행을 막는다. */
    private static final Set<String> REPLAYABLE_TOPICS = Set.of(
        KafkaTopics.CATALOG_EVENTS,
        KafkaTopics.ORDER_EVENTS,
        KafkaTopics.COUPON_ISSUE_REQUESTS,
        KafkaTopics.USER_ACTIONS
    );

    private final DlqReplayer dlqReplayer;

    @PostMapping("/replay")
    @Override
    public ApiResponse<DlqReplayV1Response> replay(@LdapAdmin AdminUser admin, @RequestParam String topic) {
        if (!REPLAYABLE_TOPICS.contains(topic)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재처리할 수 없는 토픽입니다: " + topic);
        }
        int replayed = dlqReplayer.replay(topic);
        return ApiResponse.success(new DlqReplayV1Response(topic, replayed));
    }
}
