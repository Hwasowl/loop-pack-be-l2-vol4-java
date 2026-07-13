package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryTokenServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private EntryTokenRepository entryTokenRepository;

    @InjectMocks
    private EntryTokenService entryTokenService;

    @DisplayName("토큰 발급 시")
    @Nested
    class Issue {

        @DisplayName("비어있지 않은 토큰을 5분 TTL로 발급한다")
        @Test
        void issuesTokenWithFiveMinuteTtl() {
            // when
            String token = entryTokenService.issue(USER_ID);

            // then
            assertThat(token).isNotBlank();
            verify(entryTokenRepository).issue(eq(USER_ID), anyString(), eq(Duration.ofMinutes(5)));
        }
    }

    @DisplayName("주문 관문 검증 시")
    @Nested
    class ValidateAndConsume {

        @DisplayName("토큰이 null이면 UNAUTHORIZED 예외가 발생하고 조회조차 하지 않는다")
        @Test
        void throwsUnauthorized_whenTokenIsNull() {
            // when & then
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(USER_ID, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.UNAUTHORIZED);
            verify(entryTokenRepository, never()).find(USER_ID);
        }

        @DisplayName("저장된 토큰이 없으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenNoStoredToken() {
            // given
            when(entryTokenRepository.find(USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(USER_ID, "some-token"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("토큰 값이 일치하지 않으면 UNAUTHORIZED 예외가 발생하고 삭제하지 않는다")
        @Test
        void throwsUnauthorizedAndKeepsToken_whenTokenMismatches() {
            // given
            when(entryTokenRepository.find(USER_ID)).thenReturn(Optional.of("real-token"));

            // when & then
            assertThatThrownBy(() -> entryTokenService.validateAndConsume(USER_ID, "wrong-token"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.UNAUTHORIZED);
            verify(entryTokenRepository, never()).delete(USER_ID);
        }

        @DisplayName("토큰이 일치하면 통과하고 토큰을 소비(삭제)한다")
        @Test
        void consumesToken_whenTokenMatches() {
            // given
            when(entryTokenRepository.find(USER_ID)).thenReturn(Optional.of("real-token"));

            // when
            entryTokenService.validateAndConsume(USER_ID, "real-token");

            // then
            verify(entryTokenRepository).delete(USER_ID);
        }
    }
}
