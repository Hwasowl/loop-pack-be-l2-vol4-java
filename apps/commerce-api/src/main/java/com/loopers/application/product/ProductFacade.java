package com.loopers.application.product;

import com.loopers.domain.product.ProductViewed;
import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.support.cache.CacheStore;
import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductCompositionReader reader;
    private final CacheStore cacheStore;
    private final ApplicationEventPublisher eventPublisher;

    public ProductInfo getProductDetail(Long productId) {
        ProductInfo info = cacheStore.getOrLoad(
            ProductCacheKeys.detail(productId), ProductInfo.class, ProductCacheKeys.DETAIL_TTL,
            () -> {
                ProductWithDeps c = reader.getDetail(productId);
                return ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0, c.likeCount());
            });
        // 상세 조회가 성공한 뒤(존재하지 않으면 위에서 예외)에만 조회 이벤트를 발행한다.
        eventPublisher.publishEvent(ProductViewed.of(productId));
        // 유저 행동 로그(부가) — 조회 상세는 인증이 없어 userId는 익명(null)이다.
        eventPublisher.publishEvent(UserActionEvent.of(null, "PRODUCT_VIEW", productId));
        return info;
    }

    public Page<ProductInfo> search(Long brandId, SortOption sort, Pageable pageable) {
        ProductListPage page = cacheStore.getOrLoad(
            ProductCacheKeys.list(brandId, sort, pageable), ProductListPage.class, ProductCacheKeys.LIST_TTL,
            () -> {
                Page<ProductInfo> result = reader.search(brandId, sort, pageable)
                    .map(c -> ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0, c.likeCount()));
                return new ProductListPage(result.getContent(), result.getTotalElements());
            });
        return new PageImpl<>(page.content(), pageable, page.totalElements());
    }
}
