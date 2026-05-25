package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping
    public ApiResponse<Object> like(
        @LoginUser AuthUser authUser,
        @PathVariable Long productId
    ) {
        likeFacade.like(authUser.id(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping
    public ApiResponse<Object> unlike(
        @LoginUser AuthUser authUser,
        @PathVariable Long productId
    ) {
        likeFacade.unlike(authUser.id(), productId);
        return ApiResponse.success();
    }
}
