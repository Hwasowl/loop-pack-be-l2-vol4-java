package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

public interface EntryTokenRepository {

    /** 입장 토큰 발급. ttl 이후 자동 만료된다. */
    void issue(Long userId, String token, Duration ttl);

    /** 저장된 토큰 조회. 없거나 만료면 empty. */
    Optional<String> find(Long userId);

    /** 토큰 삭제(사용 완료). 존재해서 삭제됐으면 true. */
    boolean delete(Long userId);
}
