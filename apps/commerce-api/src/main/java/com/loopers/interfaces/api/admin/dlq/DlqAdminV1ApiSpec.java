package com.loopers.interfaces.api.admin.dlq;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.dlq.dto.DlqReplayV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

@Tag(name = "DLQ Admin V1 API", description = "Loopers DLQ 재처리 어드민 API 입니다.")
public interface DlqAdminV1ApiSpec {

    @Operation(
        summary = "DLQ 재처리",
        description = "지정한 원본 토픽의 dead-letter 토픽에 격리된 메시지를 원본 토픽으로 다시 발행합니다. "
            + "어드민 인증(X-Loopers-Ldap)이 필요하며, 알 수 없는 토픽은 거부됩니다."
    )
    ApiResponse<DlqReplayV1Response> replay(@LdapAdmin AdminUser admin, @NotBlank String topic);
}
