package com.loopers.application.product;

import com.loopers.domain.product.ProductViewed;
import com.loopers.domain.product.ViewSource;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.support.cache.CacheStore;
import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductFacade {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProductCompositionReader reader;
    private final CacheStore cacheStore;
    private final ApplicationEventPublisher eventPublisher;
    private final RankingRepository rankingRepository;

    public ProductInfo getProductDetail(Long productId, ViewSource source) {
        ProductInfo info = cacheStore.getOrLoad(
            ProductCacheKeys.detail(productId), ProductInfo.class, ProductCacheKeys.DETAIL_TTL,
            () -> {
                ProductWithDeps c = reader.getDetail(productId);
                return ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0, c.likeCount());
            });
        // 상세 조회가 성공한 뒤(존재하지 않으면 위에서 예외)에만 조회 이벤트를 발행한다.
        // 유입 경로를 함께 실어, 랭킹이 스스로 만든 조회(source=RANKING)를 나중에 가려낼 수 있게 한다.
        eventPublisher.publishEvent(ProductViewed.of(productId, source));
        // 유저 행동 로그(부가) — 조회 상세는 인증이 없어 userId는 익명(null)이다.
        eventPublisher.publishEvent(UserActionEvent.of(null, "PRODUCT_VIEW", productId));
        return info.withRank(currentRankOrNull(productId));
    }

    /**
     * 당일 랭킹에서의 순위. 순위는 실시간이라 캐시 밖에서 조회한다(0-based → 1-based, 랭킹 밖이면 null).
     * <p>
     * 순위는 상세 조회의 <b>부가 정보</b>다. Redis가 죽었다고 상품 상세가 통째로 실패하면,
     * 랭킹이라는 곁가지가 본 기능을 인질로 잡는 셈이다. 조회 실패는 순위 없음(null)으로 흡수한다 —
     * 응답 계약상 "랭킹 밖"과 같은 표현이라 클라이언트가 따로 알아야 할 것도 없다.
     */
    private Long currentRankOrNull(Long productId) {
        try {
            String todayKey = RankingKeys.of(LocalDate.now(SEOUL), null);
            return rankingRepository.rank(todayKey, productId).map(r -> r + 1).orElse(null);
        } catch (Exception e) {
            log.warn("랭킹 순위 조회 실패 — 순위 없이 응답한다 (productId={})", productId, e);
            return null;
        }
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
