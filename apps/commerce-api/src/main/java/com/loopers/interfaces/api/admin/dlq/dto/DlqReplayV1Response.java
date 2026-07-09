package com.loopers.interfaces.api.admin.dlq.dto;

/** DLQ 재처리 결과. 재발행한 건수를 돌려준다. */
public record DlqReplayV1Response(String topic, int replayed) {
}
