package com.loopers.interfaces.api.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Response;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 주간·월간 랭킹 조회 E2E. 배치가 적재하는 MV 테이블을 직접 시드해, period 파라미터로
 * MV 기반 랭킹(순위·상품정보)이 반환되는지 검증한다. (배치→API 계약)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingMvApiE2ETest {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    record RankingPage(List<RankingV1Response> content, long totalElements) {
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private Long product1Id;
    private Long product2Id;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        product1Id = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        product2Id = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L)).getId();
        today = LocalDate.now(SEOUL);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        databaseCleanUp.truncateAllTables();
    }

    private void seedWeekly(String periodKey, int rankNo, Long productId, double score) {
        Timestamp now = Timestamp.from(Instant.parse("2026-07-15T00:00:00Z"));
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly "
                + "(period_key, rank_no, product_id, score, like_count, sales_count, view_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 0, 0, 0, ?, ?)",
            periodKey, rankNo, productId, score, now, now);
    }

    private ResponseEntity<ApiResponse<RankingPage>> getRankings(String query) {
        return restTemplate.exchange(
            "/api/v1/rankings?" + query, HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {});
    }

    @DisplayName("period=WEEKLY로 조회하면 주간 MV에 적재된 순위대로 상품정보가 반환된다")
    @Test
    void returnsWeeklyRanking_fromMv() {
        // given - 주간 MV에 rank 1: 맨투맨, rank 2: 후드
        String key = RankingPeriod.WEEKLY.mvPeriodKey(today);
        seedWeekly(key, 1, product2Id, 60.0);
        seedWeekly(key, 2, product1Id, 20.0);

        // when
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + today.format(YMD) + "&period=WEEKLY&size=20&page=1");

        // then
        List<RankingV1Response> content = response.getBody().data().content();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).extracting(RankingV1Response::productId).containsExactly(product2Id, product1Id),
            () -> assertThat(content).extracting(RankingV1Response::rank).containsExactly(1L, 2L),
            () -> assertThat(content.get(0).name()).isEqualTo("맨투맨"),
            () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L)
        );
    }

    @DisplayName("period=MONTHLY인데 해당 달 MV가 비어 있으면 빈 목록을 반환한다")
    @Test
    void returnsEmpty_whenMonthlyMvEmpty() {
        // when
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + today.format(YMD) + "&period=MONTHLY&size=20&page=1");

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().content()).isEmpty(),
            () -> assertThat(response.getBody().data().totalElements()).isZero()
        );
    }

    @DisplayName("알 수 없는 period 값이면 400 BAD_REQUEST를 반환한다")
    @Test
    void returns400_whenUnknownPeriod() {
        // when
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + today.format(YMD) + "&period=yearly&size=20&page=1");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
