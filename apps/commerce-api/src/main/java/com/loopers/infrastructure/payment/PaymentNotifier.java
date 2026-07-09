package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 결과 알림(부가 로직). 결제 확정·주문 반영(주요 로직)과 분리한다.
 * <p>리스너 phase 선택: 알림은 "실제로 커밋된 결제"만 보내야 하므로 AFTER_COMMIT을 쓴다
 * (로그와 달리 롤백된 결제를 알리면 안 됨). @Async로 요청/커밋 스레드와도 절연해 알림 지연·실패가 본 흐름을 안 건드린다.</p>
 * <p>실제 알림 시스템이 없어 로그 stub으로 둔다 — 운영에선 푸시/메일/SMS 발송기로 교체한다.</p>
 */
@Slf4j
@Component
public class PaymentNotifier {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentCompleted event) {
        log.info("[알림] 주문 {} 결제가 완료되었습니다.", event.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentFailed event) {
        log.info("[알림] 주문 {} 결제가 실패했습니다. 사유: {}", event.orderId(), event.reason());
    }
}
