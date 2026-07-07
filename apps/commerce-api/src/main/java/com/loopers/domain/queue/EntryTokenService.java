package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class EntryTokenService {

    /** 입장 토큰 TTL. 발급 후 이 시간 안에 주문하지 않으면 만료된다. */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final EntryTokenRepository entryTokenRepository;

    /** 대기열을 통과한 유저에게 토큰을 발급하고 값을 반환한다. */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        entryTokenRepository.issue(userId, token, TOKEN_TTL);
        return token;
    }

    /** 순번 응답에 실어줄 현재 토큰(없으면 empty). */
    public Optional<String> peek(Long userId) {
        return entryTokenRepository.find(userId);
    }

    /**
     * 주문 관문 검증. 토큰이 유효하면 소비(삭제)하고, 아니면 예외.
     * 값 불일치 시 삭제하지 않아 정상 토큰이 남는다(잘못된 헤더로 인한 삭제 방지).
     */
    public void validateAndConsume(Long userId, String token) {
        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 필요합니다. 대기열에 진입해주세요.");
        }
        String stored = entryTokenRepository.find(userId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "유효한 입장 토큰이 없습니다. 대기열에 다시 진입해주세요."));
        if (!stored.equals(token)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 일치하지 않습니다.");
        }
        entryTokenRepository.delete(userId);
    }
}
