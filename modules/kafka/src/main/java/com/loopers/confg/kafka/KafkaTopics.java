package com.loopers.confg.kafka;

/** Producer(commerce-api)와 Consumer(commerce-streamer)가 공유하는 토픽 이름 상수. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** 상품 도메인 이벤트(좋아요/조회 등). partition key = productId */
    public static final String CATALOG_EVENTS = "catalog-events";

    /** 주문/결제 이벤트. partition key = orderId */
    public static final String ORDER_EVENTS = "order-events";

    /** 선착순 쿠폰 발급 요청. partition key = couponId */
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";
}
