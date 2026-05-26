package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.SortOption;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<ProductV1Dto.PageResponse> search(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(PageSize.MAX) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductInfo> result = productFacade.search(brandId, SortOption.from(sort), pageable);
        return ApiResponse.success(ProductV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.Response> getProduct(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProductDetail(productId);
        return ApiResponse.success(ProductV1Dto.Response.from(info));
    }
}
