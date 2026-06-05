package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        stockRepository.save(new StockModel(productId, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    @DisplayName("어드민 헤더로 상품 목록을 조회하면 200 OK 를 반환한다")
    @Test
    void productSearch_returns200_withAdminHeader() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            "/api-admin/v1/products", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @DisplayName("어드민 헤더로 브랜드 목록을 조회하면 200 OK 를 반환한다")
    @Test
    void brandSearch_returns200_withAdminHeader() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            "/api-admin/v1/brands", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @DisplayName("X-Loopers-Ldap 헤더 없이 어드민 API를 호출하면 401 UNAUTHORIZED 를 반환한다")
    @Test
    void returns401_whenLdapHeaderMissing() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            "/api-admin/v1/products", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
