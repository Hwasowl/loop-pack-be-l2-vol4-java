package com.loopers.interfaces.api.admin.dlq;

import com.loopers.infrastructure.kafka.DlqReplayer;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.dlq.dto.DlqReplayV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/dlq")
@RequiredArgsConstructor
@Validated
public class DlqAdminV1Controller implements DlqAdminV1ApiSpec {

    private final DlqReplayer dlqReplayer;

    @PostMapping("/replay")
    @Override
    public ApiResponse<DlqReplayV1Response> replay(@LdapAdmin AdminUser admin, @RequestParam String topic) {
        int replayed = dlqReplayer.replay(topic);
        return ApiResponse.success(new DlqReplayV1Response(topic, replayed));
    }
}
