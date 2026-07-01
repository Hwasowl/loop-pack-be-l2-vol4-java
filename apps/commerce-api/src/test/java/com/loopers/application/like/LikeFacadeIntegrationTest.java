package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LikeFacade 통합 — Like 관계(product_like) 저장과 멱등/존재검증 흐름을 검증한다.
 * 좋아요 수 집계는 이벤트를 소비하는 commerce-streamer가 product_metrics에 반영하므로 여기서는 관계만 본다.
 */
@RecordApplicationEvents
@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    // 좋아요 이벤트 발행(Kafka)은 이 테스트 범위 밖 — 실제 브로커 의존을 끊는다.
    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 49_000L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 합성 시")
    @Nested
    class Like {

        @DisplayName("새 좋아요면 product_like 행이 추가된다")
        @Test
        void persistsLike() {
            likeFacade.like(userId, productId);
            assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도 멱등으로 행은 1개로 유지된다")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            likeFacade.like(userId, productId);
            likeFacade.like(userId, productId);
            assertThat(likeJpaRepository.count()).isEqualTo(1);
        }

        @DisplayName("새 좋아요면 유저 행동 로그(LIKE) 이벤트가 발행된다")
        @Test
        void publishesUserActionEvent_onNewLike() {
            likeFacade.like(userId, productId);

            long likeActions = applicationEvents.stream(UserActionEvent.class)
                .filter(e -> "LIKE".equals(e.action())
                    && productId.equals(e.targetId())
                    && userId.equals(e.userId()))
                .count();
            assertThat(likeActions).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND가 발생하고 like row도 생기지 않는다")
        @Test
        void throwsNotFound_andDoesNothing_whenProductMissing() {
            assertThatThrownBy(() -> likeFacade.like(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
            assertThat(likeJpaRepository.count()).isZero();
        }
    }

    @DisplayName("좋아요 취소 합성 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 row가 사라진다")
        @Test
        void deletesLike() {
            likeFacade.like(userId, productId);
            likeFacade.unlike(userId, productId);
            assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 (상품은 존재) 멱등으로 아무 일도 없다")
        @Test
        void isIdempotent_whenNothingToUnlike_andProductExists() {
            likeFacade.unlike(userId, productId);
            assertThat(likeJpaRepository.count()).isZero();
        }

        @DisplayName("존재하지 않는 상품을 unlike하면 NOT_FOUND가 발생한다")
        @Test
        void throwsNotFound_whenProductMissing() {
            assertThatThrownBy(() -> likeFacade.unlike(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
