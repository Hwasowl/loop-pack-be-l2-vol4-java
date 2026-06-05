package com.loopers.domain.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 수 동시성 검증. 여러 유저가 같은 상품에 동시에 좋아요해도 likeCount가 정확히 반영되어야 한다.
 * incrementLikeCount는 원자적 UPDATE(like_count = like_count + 1)라 read-modify-write 경합에서도 유실되지 않는다.
 */
@SpringBootTest
class LikeConcurrencyIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("여러 명이 동시에 같은 상품을 좋아요하면 좋아요 수가 정확히 사람 수만큼 증가한다")
    @Test
    void likeCountReflectsAllConcurrentLikes() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when - 서로 다른 유저 10명이 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    likeFacade.like(userId, productId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then - 좋아요 수가 정확히 10
        assertThat(productRepository.findById(productId).orElseThrow().getLikeCount()).isEqualTo(10L);
    }
}
