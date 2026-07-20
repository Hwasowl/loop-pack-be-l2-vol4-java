package com.loopers.infrastructure.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 좋아요 등록/취소(AFTER_COMMIT) → PRODUCT_LIKE_COUNT_CHANGED 발행 배선 검증.
 * 한 메시지에 두 표현(likeCount 절대값 · likeDelta ±1)이 함께 실리는지 본다 —
 * 절대값은 집계 소비자가, 증감은 랭킹 소비자가 보므로 둘 중 하나만 빠져도 한쪽 소비자가 동작하지 않는다.
 * (테스트에 트랜잭션을 걸지 않는다 — like()가 커밋돼야 AFTER_COMMIT 리스너가 발화한다)
 * <p>
 * 테스트마다 <b>다른 상품</b>을 쓴다. 발행이 @Async라 앞 테스트의 이벤트가 뒤늦게 도착할 수 있는데,
 * TRUNCATE가 AUTO_INCREMENT를 되돌려 상품 ID가 매번 같아지면 그 잔여 이벤트와 구분할 수 없다.
 */
@SpringBootTest
class LikeEventPublisherIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private LikeFacade likeFacade;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private Long likeProductId;
    private Long unlikeProductId;
    private Long idempotentProductId;

    @BeforeEach
    void setUp() {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(), any(), any());
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        likeProductId = saveProduct(brand, "후드");
        unlikeProductId = saveProduct(brand, "맨투맨");
        idempotentProductId = saveProduct(brand, "코트");
    }

    private Long saveProduct(BrandModel brand, String name) {
        return productRepository.save(new ProductModel(brand.getId(), name, "설명", 49_000L)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록하면 총량 스냅샷 1과 증감 +1을 함께 담은 이벤트가 발행된다")
    @Test
    void publishesSnapshotAndPlusOneDelta_onLike() {
        // when
        likeFacade.like(USER_ID, likeProductId);

        // then
        CatalogEventPayload liked = awaitLikeEvents(likeProductId, 1).get(0);
        assertThat(liked.likeCount()).isEqualTo(1L);
        assertThat(liked.likeDelta()).isEqualTo(1L);
    }

    @DisplayName("좋아요를 취소하면 총량 스냅샷 0과 증감 -1을 함께 담은 이벤트가 발행된다")
    @Test
    void publishesSnapshotAndMinusOneDelta_onUnlike() {
        // given
        likeFacade.like(USER_ID, unlikeProductId);

        // when
        likeFacade.unlike(USER_ID, unlikeProductId);

        // then - 등록·취소 두 건 중 마지막(취소) 이벤트를 본다
        List<CatalogEventPayload> events = awaitLikeEvents(unlikeProductId, 2);
        CatalogEventPayload unliked = events.get(events.size() - 1);
        assertThat(unliked.likeCount()).isZero();
        assertThat(unliked.likeDelta()).isEqualTo(-1L);
    }

    @DisplayName("이미 좋아요한 상품에 다시 등록하면 관계가 안 생기므로 이벤트도 발행되지 않는다")
    @Test
    void publishesNothing_whenLikeIsIdempotent() {
        // given
        likeFacade.like(USER_ID, idempotentProductId);
        awaitLikeEvents(idempotentProductId, 1);

        // when
        likeFacade.like(USER_ID, idempotentProductId);

        // then - 멱등 호출은 ProductLiked를 내지 않는다. 안 오는 것을 기다릴 수는 없으므로
        // after()로 유예를 두고 그 사이 추가 발행이 없었음을 확인한다.
        verify(kafkaTemplate, after(500).times(1))
            .send(eq(KafkaTopics.CATALOG_EVENTS), any(), argThat(p -> isLikeEventOf(p, idempotentProductId)));
    }

    /**
     * 발행이 @Async라 기대 건수가 도착할 때까지 기다린 뒤 페이로드를 모은다.
     * catalog-events에는 다른 publisher(조회 등)와 앞 테스트의 잔여 이벤트도 섞이므로 상품·타입으로 걸러낸다.
     */
    private List<CatalogEventPayload> awaitLikeEvents(Long productId, int expected) {
        verify(kafkaTemplate, timeout(3000).times(expected))
            .send(eq(KafkaTopics.CATALOG_EVENTS), any(), argThat(p -> isLikeEventOf(p, productId)));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq(KafkaTopics.CATALOG_EVENTS), any(), captor.capture());
        List<CatalogEventPayload> events = captor.getAllValues().stream()
            .map(CatalogEventPayload.class::cast)
            .filter(p -> isLikeEventOf(p, productId))
            .toList();
        assertThat(events).hasSize(expected);
        return events;
    }

    private boolean isLikeEventOf(Object value, Long productId) {
        return value instanceof CatalogEventPayload p
            && "PRODUCT_LIKE_COUNT_CHANGED".equals(p.eventType())
            && productId.equals(p.productId());
    }
}
