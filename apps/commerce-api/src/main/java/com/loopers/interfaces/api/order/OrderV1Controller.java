package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.order.dto.OrderV1Response;
import com.loopers.interfaces.api.order.dto.PlaceOrderV1Request;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;
    private final QueueFacade queueFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Response> placeOrder(
        @LoginUser AuthUser authUser,
        @RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
        @Valid @RequestBody PlaceOrderV1Request request
    ) {
        queueFacade.validateEntry(authUser.id(), entryToken);
        OrderInfo info = orderFacade.placeOrder(authUser.id(), request.toCommands(), request.couponId());
        return ApiResponse.success(OrderV1Response.from(info));
    }
}
