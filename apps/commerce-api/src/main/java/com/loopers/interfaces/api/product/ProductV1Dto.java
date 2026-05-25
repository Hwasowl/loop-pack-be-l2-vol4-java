package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductV1Dto {

    public record BrandSummary(Long id, String name) {}

    public record Response(
        Long id,
        String name,
        String description,
        Long price,
        BrandSummary brand,
        Long likeCount,
        boolean available
    ) {
        public static Response from(ProductInfo info) {
            return new Response(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                new BrandSummary(info.brandId(), info.brandName()),
                info.likeCount(),
                info.available()
            );
        }
    }

    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<ProductInfo> page) {
            return new PageResponse(
                page.getContent().stream().map(Response::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
