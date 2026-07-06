package com.loopers.domain.productmetrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 상품별 인기 지표 집계 테이블. commerce-streamer가 이벤트를 소비해 upsert한다.
 * product_id를 PK로 두어 상품당 1행을 보장한다.
 */
@Getter
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_updated_at")
    private ZonedDateTime likeUpdatedAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {
    }

    private ProductMetrics(Long productId) {
        this.productId = productId;
    }

    public static ProductMetrics init(Long productId) {
        return new ProductMetrics(productId);
    }

    /**
     * 좋아요 총량 스냅샷을 최신-우선으로 반영한다. 절대값 덮어쓰기라, 늦게 도착한 과거 스냅샷은 버려야
     * 정합성이 유지된다 — eventAt이 마지막 반영 시각보다 이후일 때만 덮어쓴다.
     * @return 실제로 반영했으면 true, 오래된 이벤트라 버렸으면 false
     */
    public boolean applyLikeSnapshot(long likeCount, ZonedDateTime eventAt) {
        if (this.likeUpdatedAt != null && !eventAt.isAfter(this.likeUpdatedAt)) {
            return false;
        }
        this.likeCount = Math.max(0, likeCount);
        this.likeUpdatedAt = eventAt;
        return true;
    }

    /** 조회 수 증가. */
    public void addView(long delta) {
        this.viewCount = Math.max(0, this.viewCount + delta);
    }

    /** 판매 수량 증가. */
    public void addSales(long delta) {
        this.salesCount = Math.max(0, this.salesCount + delta);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = ZonedDateTime.now();
    }
}
