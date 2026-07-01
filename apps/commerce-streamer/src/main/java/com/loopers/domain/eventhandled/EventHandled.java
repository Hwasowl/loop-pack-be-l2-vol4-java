package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 소비 멱등 처리를 위한 이벤트 처리 이력. event_id를 PK로 두어 같은 이벤트의 중복 소비를 차단한다.
 * (At Least Once 발행으로 같은 메시지가 두 번 도착해도 최종 반영은 한 번만 보장)
 */
@Getter
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {
    }

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }
}
