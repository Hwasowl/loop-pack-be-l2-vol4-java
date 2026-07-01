package com.loopers.domain.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 관계 동시성 검증. 여러 유저가 같은 상품에 동시에 좋아요해도 product_like 행이 정확히 사람 수만큼 생겨야 한다.
 * (user_id, product_id) 유니크 제약이 중복을 차단한다. 좋아요 수 집계는 streamer 소관이므로 여기서는 관계 행만 본다.
 */
@SpringBootTest
class LikeConcurrencyIntegrationTest {

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

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("여러 명이 동시에 같은 상품을 좋아요하면 like 행이 정확히 사람 수만큼 생긴다")
    @Test
    void rowsReflectAllConcurrentLikes() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when - 서로 다른 유저 10명이 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.like(userId, productId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - like 행이 정확히 10
        assertThat(likeJpaRepository.count()).isEqualTo(10);
    }

    @DisplayName("여러 명이 동시에 같은 상품을 좋아요/취소하면 like 행이 정확히 0으로 정리된다")
    @Test
    void rowsNetToZero_whenAllUnlikeAfterLike() throws InterruptedException {
        // given - 상품 + 10명이 미리 좋아요
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();

        int threadCount = 10;
        for (int i = 0; i < threadCount; i++) {
            likeFacade.like((long) (i + 1), productId);
        }
        assertThat(likeJpaRepository.count()).isEqualTo(10);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when - 좋아요한 10명이 동시에 취소
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.unlike(userId, productId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - like 행이 정확히 0 (유실 없음)
        assertThat(likeJpaRepository.count()).isZero();
    }
}
