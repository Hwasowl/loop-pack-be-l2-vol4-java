package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ViewSource;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 랭킹 조회가 실패해도 상품 상세는 살아남는지 검증한다.
 * 순위는 상세 응답의 곁가지라, Redis 장애가 본 기능을 인질로 잡으면 안 된다.
 */
@SpringBootTest
class ProductFacadeRankingResilienceTest {

    @Autowired
    private ProductFacade productFacade;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    // 랭킹 저장소만 Redis 장애를 흉내내도록 바꾼다(상세 조회 캐시는 실제 Redis 그대로).
    @MockitoBean
    private RankingRepository rankingRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        stockRepository.save(new StockModel(productId, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("랭킹 조회가 Redis 장애로 실패해도 상품 상세는 정상 반환되고 순위만 null이 된다")
    @Test
    void returnsDetailWithNullRank_whenRankingLookupFails() {
        // given - 순위 조회가 Redis 타임아웃으로 실패한다
        when(rankingRepository.rank(anyString(), any()))
            .thenThrow(new QueryTimeoutException("redis down"));

        // when
        ProductInfo info = productFacade.getProductDetail(productId, ViewSource.SEARCH);

        // then - 상세는 살아 있고, 순위만 빠진다("랭킹 밖"과 같은 표현이라 응답 계약이 깨지지 않는다)
        assertAll(
            () -> assertThat(info.id()).isEqualTo(productId),
            () -> assertThat(info.name()).isEqualTo("후드"),
            () -> assertThat(info.rank()).isNull()
        );
    }
}
