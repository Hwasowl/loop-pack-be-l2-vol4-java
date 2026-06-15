# Round 5 — 상품 목록 조회 인덱스 최적화 벤치마크

판단의 근거와 트레이드오프는 [00_decisions.md](00_decisions.md) 참고. 이 문서는 **측정 데이터(AS-IS / TO-BE)** 만 담는다.

## 측정 환경

| 항목 | 값 |
|---|---|
| DB | MySQL 8.0 (docker `infra-compose.yml`) |
| 데이터 | `product` 10만건 (시딩 스크립트 [01_seed.sql](01_seed.sql)) |
| 분포 | brand_id 1~100 균등 / price 1,043~1,999,988 / like_count 0~99,996(멱함수 편향) / deleted ≈2%(1,968건) |
| 측정 | `EXPLAIN ANALYZE`, LIMIT 20 |

브랜드별 행 수 예: brand 78 → 1,075건, brand 1 → 약 980건(deleted 제외 964).

## AS-IS — 인덱스 PK 외 전무

모든 쿼리가 **`Table scan on product` (10만 행) + filesort**.

| 쿼리 (LIMIT 20) | actual time | 접근 | 정렬 |
|---|---|---|---|
| brand=1 + `price ASC, id DESC` | 23.1 ms | 풀스캔 100k → 964 | filesort |
| brand=1 + `like_count DESC, id DESC` | 22.6 ms | 풀스캔 100k → 964 | filesort |
| 전역 + `like_count DESC, id DESC` | 31.6 ms | 풀스캔 100k → 98,032 | filesort |
| 전역 + `price ASC` `OFFSET 50000` | 66.1 ms | 풀스캔 100k | 50,020행 정렬 |

```
-> Limit: 20 row(s)
    -> Sort: product.like_count DESC, product.id DESC, limit input to 20 row(s) per chunk
        -> Filter: (product.deleted_at is null)
            -> Table scan on product  (rows=100000)
```

## 적용한 인덱스

```sql
CREATE INDEX idx_product_brand_like    ON product (brand_id, like_count);
CREATE INDEX idx_product_brand_price   ON product (brand_id, price);
CREATE INDEX idx_product_brand_created ON product (brand_id, created_at);
CREATE INDEX idx_product_like          ON product (like_count);
```

코드 반영: `ProductModel`의 `@Table(indexes = {...})` → `ddl-auto`가 스키마에 생성.
함께 변경: `PRICE_ASC` 정렬의 tiebreak을 `id DESC` → `id ASC`로 (정렬 방향 일치 — 근거는 00_decisions.md).

## TO-BE — 인덱스 + tiebreak 방향 정렬

전부 **Sort 노드 제거(filesort 없음)**, ordered/reverse 인덱스 스캔.

| 쿼리 (LIMIT 20) | AS-IS | TO-BE | 개선 | 실행계획 |
|---|---|---|---|---|
| brand=1 + `like_count DESC, id DESC` | 22.6 ms | **0.34 ms** | ≈66× | `idx_product_brand_like` (reverse) |
| brand=1 + `price ASC, id ASC` | 23.1 ms | **0.26 ms** | ≈89× | `idx_product_brand_price` |
| brand=1 + `created_at DESC, id DESC` | 풀스캔 | **0.23 ms** | — | `idx_product_brand_created` (reverse) |
| 전역 + `like_count DESC, id DESC` | 31.6 ms | **0.06 ms** | ≈500× | `idx_product_like` (reverse) |

```
-> Limit: 20 row(s)
    -> Filter: (product.deleted_at is null)
        -> Index lookup on product using idx_product_brand_like (brand_id=1) (reverse)
```

## 미해결로 남긴 갭 (의도적 — silent 처리 아님)

| 갭 | 현재 | 해소 시점 |
|---|---|---|
| 전역 `price ASC` / `latest` 정렬 | 인덱스 없음 → 풀스캔 | 수요 관측 시 `(price)` / `(created_at)` 단일 인덱스 추가 |
| 깊은 OFFSET (`OFFSET 50000`) | 인덱스 무관하게 비쌈(5만 행 skip) | OFFSET → 커서 기반 페이지네이션 전환(별도 과제) |

## tiebreak 방향의 영향 (검증 데이터)

| 정렬 | tiebreak | 결과 |
|---|---|---|
| `price ASC` | `id DESC` (엇갈림) | filesort 잔존, `idx_product_brand_price` 미사용 → 1.81 ms |
| `price ASC` | `id ASC` (일치) | filesort 제거, `idx_product_brand_price` ordered 스캔 → 0.26 ms |
