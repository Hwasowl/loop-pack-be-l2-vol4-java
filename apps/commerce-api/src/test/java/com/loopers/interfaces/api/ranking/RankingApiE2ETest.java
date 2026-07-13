package com.loopers.interfaces.api.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.LikeCountSeeder;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Response;
import com.loopers.interfaces.api.ranking.dto.RankingV1Response;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingApiE2ETest {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** Spring Page 응답에서 필요한 필드만 부분 역직렬화한다. */
    record RankingPage(List<RankingV1Response> content, long totalElements) {
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private LikeCountSeeder likeCountSeeder;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private Long product1Id;
    private Long product2Id;
    private Long product3Id;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        product1Id = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        product2Id = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L)).getId();
        product3Id = productRepository.save(new ProductModel(brand.getId(), "코트", "따뜻함", 90_000L)).getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 10));
        stockRepository.save(new StockModel(product3Id, 10));
        today = LocalDate.now(SEOUL);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        likeCountSeeder.clear();
        redisCleanUp.truncateAll();
    }

    private void seedScore(String key, Long productId, double score) {
        redisTemplate.opsForZSet().add(key, RankingKeys.member(productId), score);
    }

    private ResponseEntity<ApiResponse<RankingPage>> getRankings(String query) {
        return restTemplate.exchange(
            "/api/v1/rankings?" + query, HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {}
        );
    }

    @DisplayName("일별 랭킹을 조회하면 점수 내림차순으로 순위·상품정보(좋아요 수 포함)가 반환된다")
    @Test
    void returnsDailyRanking_orderedByScoreDesc_withAggregation() {
        // given - 점수: 코트(300) > 후드(200) > 맨투맨(100), 후드는 좋아요 7
        String key = RankingKeys.of(today, null);
        seedScore(key, product3Id, 300);
        seedScore(key, product1Id, 200);
        seedScore(key, product2Id, 100);
        likeCountSeeder.seed(product1Id, 7L);

        // when
        ResponseEntity<ApiResponse<RankingPage>> response = getRankings("date=" + today.format(YMD) + "&size=20&page=1");

        // then
        List<RankingV1Response> content = response.getBody().data().content();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).hasSize(3),
            () -> assertThat(content).extracting(RankingV1Response::productId)
                .containsExactly(product3Id, product1Id, product2Id),
            () -> assertThat(content).extracting(RankingV1Response::rank).containsExactly(1L, 2L, 3L),
            () -> assertThat(content.get(0).name()).isEqualTo("코트"),
            () -> assertThat(content.get(1).likeCount()).isEqualTo(7L),
            () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3L)
        );
    }

    @DisplayName("hour 파라미터를 주면 해당 시간대(시간별 키) 랭킹을 조회한다")
    @Test
    void returnsHourlyRanking_whenHourGiven() {
        // given - 같은 날, 시간별 키에는 맨투맨만 최상위로 적재
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        int hour = now.getHour();
        String hourlyKey = RankingKeys.of(today, hour);
        seedScore(hourlyKey, product2Id, 500);

        // when
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + today.format(YMD) + "&hour=" + hour + "&size=20&page=1");

        // then - 시간별 키의 내용(맨투맨 1위)만 반환된다
        List<RankingV1Response> content = response.getBody().data().content();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).extracting(RankingV1Response::productId).containsExactly(product2Id),
            () -> assertThat(content.get(0).rank()).isEqualTo(1L)
        );
    }

    @DisplayName("해당 날짜에 랭킹 데이터가 없으면 빈 목록을 반환한다")
    @Test
    void returnsEmpty_whenNoDataForDate() {
        // when - 아무 것도 적재하지 않은 날짜
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + today.format(YMD) + "&size=20&page=1");

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().content()).isEmpty(),
            () -> assertThat(response.getBody().data().totalElements()).isZero()
        );
    }

    @DisplayName("date 형식이 yyyyMMdd가 아니면 400 BAD_REQUEST를 반환한다")
    @Test
    void returns400_whenInvalidDateFormat() {
        // when
        ResponseEntity<ApiResponse<RankingPage>> response = getRankings("date=2026-01-13&size=20&page=1");

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
        );
    }

    @DisplayName("일자가 바뀌어도 date 파라미터로 이전 날짜의 랭킹을 조회할 수 있다")
    @Test
    void returnsPastDateRanking_byDateParam() {
        // given - 어제 날짜 키에 코트만 적재
        LocalDate yesterday = today.minusDays(1);
        seedScore(RankingKeys.of(yesterday, null), product3Id, 300);

        // when - 어제 날짜로 조회
        ResponseEntity<ApiResponse<RankingPage>> response =
            getRankings("date=" + yesterday.format(YMD) + "&size=20&page=1");

        // then
        List<RankingV1Response> content = response.getBody().data().content();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).extracting(RankingV1Response::productId).containsExactly(product3Id)
        );
    }

    @DisplayName("상품 상세 조회 시 당일 랭킹에 있으면 순위가, 없으면 rank가 null로 반환된다")
    @Test
    void detail_includesRank_whenRanked_elseNull() {
        // given - 당일 키에 코트(300) > 후드(200)만 적재(맨투맨은 미적재)
        String key = RankingKeys.of(today, null);
        seedScore(key, product3Id, 300);
        seedScore(key, product1Id, 200);

        // when - 랭킹 2위인 후드 / 랭킹에 없는 맨투맨 상세
        ResponseEntity<ApiResponse<ProductV1Response>> ranked = restTemplate.exchange(
            "/api/v1/products/" + product1Id, HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<ProductV1Response>> unranked = restTemplate.exchange(
            "/api/v1/products/" + product2Id, HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {});

        // then
        assertAll(
            () -> assertThat(ranked.getBody().data().rank()).isEqualTo(2L),
            () -> assertThat(unranked.getBody().data().rank()).isNull()
        );
    }
}
