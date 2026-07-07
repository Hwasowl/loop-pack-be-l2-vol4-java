package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.queue.dto.EnterQueueV1Response;
import com.loopers.interfaces.api.queue.dto.QueuePositionV1Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<EnterQueueV1Response> enter(@LoginUser AuthUser authUser) {
        return ApiResponse.success(EnterQueueV1Response.from(queueFacade.enter(authUser.id())));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueuePositionV1Response> position(@LoginUser AuthUser authUser) {
        return ApiResponse.success(QueuePositionV1Response.from(queueFacade.position(authUser.id())));
    }
}
