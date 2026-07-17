package com.loopers.domain.productmetrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 상품별 <b>시간 단위 원천 집계(SOT)</b>. "그 시간에 무슨 일이 일어났는가"를 가중치를 곱하기 <b>전</b>
 * 상태로 남긴다.
 * <p>
 * product_metrics(현재 상태의 누적 카운터)와 목적이 다르다 — 저쪽은 "지금 이 상품의 좋아요가 몇 개인가"를,
 * 이쪽은 "7월 17일 14시에 조회 320건·좋아요 +5·주문 15만원이 있었다"를 답한다. 후자는 시간이 지나면
 * 어디서도 복원할 수 없다(Redis ZSET엔 이미 곱해진 점수만 남고, Kafka는 리텐션이 지나면 사라진다).
 * <p>
 * 이 표가 있어야 가능해지는 것:
 * <ul>
 *   <li>Redis 유실 시 ZSET 재구성 — 랭킹은 이 사실들의 파생물이다.</li>
 *   <li>가중치 변경 — 새 가중치를 이 원본에 곱해 다른 버전의 랭킹을 만들 수 있다.</li>
 * </ul>
 * 버킷 경계는 이벤트 발생 시각(Asia/Seoul 정시 절삭)이다. 지금 시각이 아닌 발생 시각을 쓰는 이유는
 * 지연·재처리된 이벤트가 엉뚱한 시간대에 기록되는 것을 막기 위함이다.
 */
@Getter
@Entity
@Table(
        name = "product_metrics_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_metrics_hourly", columnNames = {"product_id", "bucket_hour"}),
        indexes = @Index(name = "idx_product_metrics_hourly_bucket", columnList = "bucket_hour")
)
public class ProductMetricsHourly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 집계 구간의 시작 정시 (Asia/Seoul 기준으로 절삭된 시각). */
    @Column(name = "bucket_hour", nullable = false)
    private ZonedDateTime bucketHour;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    /** 좋아요 순증감의 합. 취소가 많으면 음수가 될 수 있다. */
    @Column(name = "like_delta", nullable = false)
    private long likeDelta;

    /** 주문 금액(단가×수량)의 합. */
    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    protected ProductMetricsHourly() {
    }

    private ProductMetricsHourly(Long productId, ZonedDateTime bucketHour) {
        this.productId = productId;
        this.bucketHour = bucketHour;
    }

    public static ProductMetricsHourly init(Long productId, ZonedDateTime bucketHour) {
        return new ProductMetricsHourly(productId, bucketHour);
    }

    public void addView(long delta) {
        this.viewCount += delta;
    }

    /** 좋아요 증감을 누적한다. 음수(취소)도 그대로 더한다 — 그 시간에 실제로 일어난 일이기 때문이다. */
    public void addLikeDelta(long delta) {
        this.likeDelta += delta;
    }

    public void addOrderAmount(long amount) {
        this.orderAmount += amount;
    }
}
