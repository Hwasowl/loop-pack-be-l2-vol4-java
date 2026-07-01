-- product_metrics / event_handled는 commerce-streamer가 소유하는 테이블이다.
-- commerce-api는 이들을 native 쿼리로만 참조(매핑 없음)하므로, H2 통합 테스트에서 수동으로 생성한다.
CREATE TABLE IF NOT EXISTS product_metrics (
    product_id  BIGINT PRIMARY KEY,
    like_count  BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    view_count  BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS event_handled (
    event_id   VARCHAR(255) PRIMARY KEY,
    handled_at TIMESTAMP NOT NULL
);
