package com.loopers.confg.kafka;

/**
 * dead-letter 토픽에 실리는 봉투. 원본 페이로드를 문자열로 보존해 사람이 원인을 보고 재발행을 판단한다.
 */
public record DlqMessage(String originalTopic, String key, String payload, String error) {
}
