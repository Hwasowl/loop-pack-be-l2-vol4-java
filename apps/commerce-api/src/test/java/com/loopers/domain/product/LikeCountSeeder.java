package com.loopers.domain.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 좋아요 수를 직접 시드한다.
 * <p>운영에선 좋아요 이벤트를 commerce-streamer가 소비해 product_metrics.like_count에 집계하므로,
 * 정렬·표시 테스트는 그 경로를 우회해 product_metrics에 네이티브로 시드한다.
 * commerce-api는 product_metrics를 매핑하지 않으므로(DatabaseCleanUp이 정리 못 함) clear()를 별도로 제공한다.</p>
 */
@Component
public class LikeCountSeeder {

    private final JdbcTemplate jdbcTemplate;

    public LikeCountSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void seed(Long productId, long likeCount) {
        // 멱등 upsert — 공유 H2 인메모리라 이전 테스트가 남긴 행과 충돌하지 않도록 MERGE로 덮어쓴다.
        jdbcTemplate.update(
            "MERGE INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at) "
                + "KEY(product_id) VALUES (?, ?, 0, 0, CURRENT_TIMESTAMP)",
            productId, likeCount);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM product_metrics");
    }
}
