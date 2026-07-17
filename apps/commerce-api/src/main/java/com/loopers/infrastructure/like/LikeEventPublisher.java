package com.loopers.infrastructure.like;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.ProductLiked;
import com.loopers.domain.like.ProductUnliked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;

/**
 * 좋아요 등록/취소가 커밋된 뒤, 그 상품의 <b>현재 총 좋아요 수 스냅샷</b>과 <b>이번 증감(±1)</b>을 함께
 * catalog-events로 발행한다. 절대값은 집계 소비자(product_metrics)가 최신-우선(occurredAt 비교)으로
 * 덮어쓰는 데 쓰고 — 중복·유실에 강하다(다음 스냅샷이 자동 교정) — 증감은 랭킹 소비자가 ZSET에 가산하는 데 쓴다.
 * 둘 다 여기서 싣는 이유는, 증감을 안 보내면 랭킹 소비자가 이전 값을 알아내려 집계 테이블을 읽어야 하고
 * 그 순간 두 소비자의 장애·재처리가 한 흐름으로 묶이기 때문이다.
 * key=productId 로 같은 상품 이벤트의 파티션 순서를 보장한다.
 * (원본 product_like가 있어 유실돼도 재집계로 복구 가능하므로 Outbox 없이 직접 발행한다)
 * @Async — 총량 count 조회·발행을 요청 스레드에서 분리한다(요청 지연 방지). 스레드풀 포화 감지는 미보강 TODO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventPublisher {

    private static final String TYPE_LIKE_COUNT_CHANGED = "PRODUCT_LIKE_COUNT_CHANGED";

    private final LikeRepository likeRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(ProductLiked event) {
        publish(event.eventId(), event.productId(), event.userId(), event.occurredAt(), 1L);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(ProductUnliked event) {
        publish(event.eventId(), event.productId(), event.userId(), event.occurredAt(), -1L);
    }

    private void publish(String eventId, Long productId, Long userId, ZonedDateTime occurredAt, long likeDelta) {
        long likeCount = likeRepository.countByProductId(productId);
        CatalogEventPayload payload = new CatalogEventPayload(
                eventId, TYPE_LIKE_COUNT_CHANGED, productId, userId, occurredAt.toString(),
                null, likeCount, likeDelta, null, null);
        try {
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // 비동기 전송 실패(브로커 nack 등)는 동기 catch에 안 잡히므로 여기서 관측한다.
                        log.warn("좋아요 스냅샷 발행 실패(async) (productId={})", productId, ex);
                    }
                });
        } catch (Exception e) {
            // 좋아요 관계(product_like)는 이미 커밋됐다. 발행 실패로 API를 500 내지 않고, 집계 이벤트만 버린다.
            // 유실은 허용 — 원본이 남아 다음 스냅샷/재집계로 복구할 수 있다.
            log.warn("좋아요 스냅샷 발행 실패 (productId={})", productId, e);
        }
    }
}
