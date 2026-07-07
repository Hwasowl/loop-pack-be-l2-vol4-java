package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.queue.dto.EnterQueueV1Response;
import com.loopers.interfaces.api.queue.dto.QueuePositionV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "Loopers 주문 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "대기열에 진입하고 현재 순번을 반환합니다. 이미 대기 중이면 기존 순번을 유지합니다."
    )
    ApiResponse<EnterQueueV1Response> enter(AuthUser authUser);

    @Operation(
        summary = "대기열 순번 조회",
        description = "현재 순번과 예상 대기 시간을 반환합니다. 입장 차례가 되면 입장 토큰이 함께 내려갑니다."
    )
    ApiResponse<QueuePositionV1Response> position(AuthUser authUser);
}
