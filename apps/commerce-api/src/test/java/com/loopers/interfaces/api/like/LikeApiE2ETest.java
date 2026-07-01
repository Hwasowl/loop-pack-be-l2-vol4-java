package com.loopers.interfaces.api.like;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 API E2E. 좋아요 수 집계는 streamer 소관이므로, 여기서는 API 동작과 관계(product_like) 반영을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeApiE2ETest {

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
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

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
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private String likeUrl() {
        return "/api/v1/products/" + productId + "/likes";
    }

    @DisplayName("좋아요를 등록하면 200 OK이고 product_like 행이 1개 생긴다")
    @Test
    void like_persistsRow() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            likeUrl(), HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeJpaRepository.count()).isEqualTo(1);
    }

    @DisplayName("같은 상품을 두 번 좋아요해도 행은 1개로 유지된다(멱등)")
    @Test
    void like_isIdempotent() {
        ResponseEntity<ApiResponse<Object>> first = restTemplate.exchange(
            likeUrl(), HttpMethod.POST, new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<>() {});
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Object>> second = restTemplate.exchange(
            likeUrl(), HttpMethod.POST, new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<>() {});

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeJpaRepository.count()).isEqualTo(1);
    }

    @DisplayName("좋아요 후 취소하면 행이 사라진다")
    @Test
    void unlike_deletesRow() {
        restTemplate.exchange(likeUrl(), HttpMethod.POST, new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<ApiResponse<Object>>() {});
        ResponseEntity<ApiResponse<Object>> deleteRes = restTemplate.exchange(
            likeUrl(), HttpMethod.DELETE, new HttpEntity<>(userHeaders()), new ParameterizedTypeReference<>() {});

        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeJpaRepository.count()).isZero();
    }

    @DisplayName("인증 헤더 없이 좋아요하면 401 UNAUTHORIZED 를 반환한다")
    @Test
    void like_returns401_whenUnauthenticated() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            likeUrl(), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
