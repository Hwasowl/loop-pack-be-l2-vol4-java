package com.loopers.interfaces.api.queue;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.dto.OrderV1Response;
import com.loopers.interfaces.api.order.dto.PlaceOrderV1Request;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "queue.entry.enabled=true"
)
class QueueGateApiE2ETest {

    private static final String ORDER_ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        userFacade.signUp(LOGIN_ID, PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@loopers.com");
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        stockRepository.save(new StockModel(productId, 10));
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

    private PlaceOrderV1Request orderRequest() {
        return new PlaceOrderV1Request(List.of(
            new PlaceOrderV1Request.OrderLineV1Request(productId, 1)
        ), null);
    }

    /** 스케줄러(@Profile("!test") 로 미기동)를 대신해 대기열에서 한 명을 꺼내 토큰을 발급하고 값을 반환한다. */
    private String simulateSchedulerAndIssueToken() {
        List<Long> polled = queueService.pollForEntry(14);
        assertThat(polled).isNotEmpty();
        return entryTokenService.issue(polled.get(0));
    }

    @DisplayName("게이트가 켜진 상태에서 입장 토큰 없이 주문하면 401 UNAUTHORIZED 를 반환한다")
    @Test
    void returns401_whenOrderWithoutEntryToken() {
        // when
        ResponseEntity<ApiResponse<OrderV1Response>> response = restTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest(), userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("대기열 진입 후 발급받은 토큰으로 주문하면 200 OK 이고, 같은 토큰을 재사용하면 소비되어 401 이다")
    @Test
    void succeedsWithToken_thenRejectsReuse() {
        // given - 진입 후 스케줄러 대행으로 토큰 발급
        restTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST,
            new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<ApiResponse<Object>>() {});
        String token = simulateSchedulerAndIssueToken();

        HttpHeaders headers = userHeaders();
        headers.set("X-Entry-Token", token);

        // when - 유효 토큰으로 주문
        ResponseEntity<ApiResponse<OrderV1Response>> first = restTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest(), headers),
            new ParameterizedTypeReference<>() {}
        );

        // then - 통과
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // when - 같은 토큰 재사용
        ResponseEntity<ApiResponse<OrderV1Response>> second = restTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderRequest(), headers),
            new ParameterizedTypeReference<>() {}
        );

        // then - 이미 소비되어 거절
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
