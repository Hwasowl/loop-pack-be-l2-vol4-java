package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingService;
import com.loopers.confg.kafka.DlqPublisher;
import com.loopers.confg.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * catalog-events → 랭킹 배선 검증. 이벤트 종류별로 올바른 점수 반영이 호출되는지 본다.
 * 가중치 계산 자체는 RankingServiceTest가, ZSET 명령은 RedisRankingRepository가 각각 책임진다.
 */
@ExtendWith(MockitoExtension.class)
class RankingEventsConsumerTest {

    private static final String OCCURRED_AT = "2026-01-13T15:30:00+09:00";
    private static final ZonedDateTime T = ZonedDateTime.parse(OCCURRED_AT);
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private RankingService rankingService;
    @Mock
    private DlqPublisher dlqPublisher;
    @Mock
    private Acknowledgment acknowledgment;

    private RankingEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RankingEventsConsumer(rankingService, new ObjectMapper(), dlqPublisher);
    }

    /** 발행 측 페이로드와 같은 필드 순서의 JSON을 만든다. */
    private String json(String eventType, String extraFields) {
        return """
            {"eventId":"e-1","eventType":"%s","productId":%d,"userId":null,"occurredAt":"%s"%s}
            """.formatted(eventType, PRODUCT_ID, OCCURRED_AT, extraFields);
    }

    private void consume(String payload) {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
            KafkaTopics.CATALOG_EVENTS, 0, 0L, PRODUCT_ID.toString(),
            payload.getBytes(StandardCharsets.UTF_8));
        consumer.consume(List.of(record), acknowledgment);
    }

    @DisplayName("조회 이벤트를 소비하면")
    @Nested
    class Viewed {

        @DisplayName("발생 시각과 상품으로 조회 점수 반영이 호출된다")
        @Test
        void appliesView() {
            // when
            consume(json("PRODUCT_VIEWED", ""));

            // then
            verify(rankingService).applyView(T, PRODUCT_ID);
            verify(acknowledgment).acknowledge();
        }
    }

    @DisplayName("좋아요 이벤트를 소비하면")
    @Nested
    class LikeCountChanged {

        @DisplayName("발행 측이 실은 증감(+1)이 그대로 델타로 반영된다")
        @Test
        void appliesPublishedDelta() {
            // when - 총량 스냅샷(7)이 함께 와도 랭킹은 증감만 본다
            consume(json("PRODUCT_LIKE_COUNT_CHANGED", ",\"likeCount\":7,\"likeDelta\":1"));

            // then
            verify(rankingService).applyLikeDelta(T, PRODUCT_ID, 1L);
        }

        @DisplayName("좋아요 취소(-1)면 음수 델타가 그대로 반영된다")
        @Test
        void appliesNegativeDelta() {
            // when
            consume(json("PRODUCT_LIKE_COUNT_CHANGED", ",\"likeCount\":0,\"likeDelta\":-1"));

            // then
            verify(rankingService).applyLikeDelta(T, PRODUCT_ID, -1L);
        }

        @DisplayName("likeDelta가 없으면 점수를 반영하지 않고 DLQ로 격리한다")
        @Test
        void quarantines_whenDeltaMissing() {
            // when - 구버전 발행 측이 보낸, 증감 없는 메시지
            consume(json("PRODUCT_LIKE_COUNT_CHANGED", ",\"likeCount\":7"));

            // then
            verify(rankingService, never()).applyLikeDelta(any(), anyLong(), anyLong());
            verify(dlqPublisher).publish(eq(KafkaTopics.CATALOG_EVENTS), any(), any());
        }
    }

    @DisplayName("판매 이벤트를 소비하면")
    @Nested
    class Sold {

        @DisplayName("단가×수량으로 계산한 주문 금액이 반영된다")
        @Test
        void appliesAmountAsUnitPriceTimesQuantity() {
            // when - 5만원 × 3개
            consume(json("PRODUCT_SOLD", ",\"quantity\":3,\"unitPrice\":50000"));

            // then
            verify(rankingService).applyOrder(T, PRODUCT_ID, 150_000L);
        }

        @DisplayName("단가가 없으면 금액 0으로 반영해 점수를 올리지 않는다")
        @Test
        void appliesZero_whenUnitPriceMissing() {
            // when
            consume(json("PRODUCT_SOLD", ",\"quantity\":3"));

            // then
            verify(rankingService).applyOrder(T, PRODUCT_ID, 0L);
        }
    }

    @DisplayName("알 수 없는 이벤트 타입은 무시하고 DLQ로도 보내지 않는다")
    @Test
    void ignoresUnknownType() {
        // when
        consume(json("PRODUCT_ARCHIVED", ""));

        // then - 처리 실패가 아니라 관심 밖이므로 격리 대상이 아니다
        verifyNoInteractions(rankingService);
        verifyNoInteractions(dlqPublisher);
        verify(acknowledgment).acknowledge();
    }

    @DisplayName("역직렬화할 수 없는 메시지는 DLQ로 격리하고 배치는 계속 커밋된다")
    @Test
    void quarantines_whenPayloadBroken() {
        // when
        consume("{ this is not json");

        // then - 파티션을 막지 않는다
        verifyNoInteractions(rankingService);
        verify(dlqPublisher).publish(eq(KafkaTopics.CATALOG_EVENTS), any(), any());
        verify(acknowledgment).acknowledge();
    }
}
