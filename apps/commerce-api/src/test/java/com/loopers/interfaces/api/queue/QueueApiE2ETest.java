package com.loopers.interfaces.api.queue;

import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.dto.EnterQueueV1Response;
import com.loopers.interfaces.api.queue.dto.QueuePositionV1Response;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        userFacade.signUp(LOGIN_ID, PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@loopers.com");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    @DisplayName("POST /api/v1/queue/enter — 처음 진입하면 200 OK이고 순번 1과 대기 인원 1을 반환한다")
    @Test
    void returns200_andFirstPosition_onEnter() {
        // when
        ResponseEntity<ApiResponse<EnterQueueV1Response>> response = restTemplate.exchange(
            "/api/v1/queue/enter", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getBody()).isNotNull();
        EnterQueueV1Response data = response.getBody().data();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(data.position()).isEqualTo(1L),
            () -> assertThat(data.waitingCount()).isEqualTo(1L)
        );
    }

    @DisplayName("GET /api/v1/queue/position — 진입 후 조회하면 순번 1이고 아직 토큰은 없다")
    @Test
    void returnsPositionWithoutToken_beforeSchedulerRuns() {
        // given
        restTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST,
            new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<ApiResponse<EnterQueueV1Response>>() {});

        // when
        ResponseEntity<ApiResponse<QueuePositionV1Response>> response = restTemplate.exchange(
            "/api/v1/queue/position", HttpMethod.GET, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getBody()).isNotNull();
        QueuePositionV1Response data = response.getBody().data();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(data.position()).isEqualTo(1L),
            () -> assertThat(data.token()).isNull()
        );
    }

    @DisplayName("이미 대기 중인 유저가 다시 진입해도 순번이 앞당겨지지 않고 대기 인원도 그대로다")
    @Test
    void keepsPosition_onReenter() {
        // given
        restTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST,
            new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<ApiResponse<EnterQueueV1Response>>() {});

        // when
        ResponseEntity<ApiResponse<EnterQueueV1Response>> response = restTemplate.exchange(
            "/api/v1/queue/enter", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().waitingCount()).isEqualTo(1L);
        assertThat(response.getBody().data().position()).isEqualTo(1L);
    }
}
