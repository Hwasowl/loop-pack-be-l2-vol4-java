# Round 5 벤치마크 러너

상품 10만건 시딩 + 인덱스 AS-IS/TO-BE 측정을 **한 번에 재현**하는 스크립트. docker MySQL(`docker-mysql-1`)이 떠 있어야 한다.

## 전제

```powershell
docker ps   # docker-mysql-1, redis-master 등이 Up 상태인지 확인
```
스키마(`product` 테이블)가 없으면 commerce-api를 `local` 프로필로 한 번 부팅하면 Hibernate가 생성한다.

## 사용법

PowerShell에서:

```powershell
# 전체 랩: 시딩 → AS-IS(인덱스 제거 후 EXPLAIN) → TO-BE(인덱스 생성 후 EXPLAIN) → tiebreak 비교
.\.docs\round5\benchmark\run-benchmark.ps1 -Action all

# 개별 액션
.\.docs\round5\benchmark\run-benchmark.ps1 -Action seed          # 10만건 재시딩(truncate 후)
.\.docs\round5\benchmark\run-benchmark.ps1 -Action drop-index    # 인덱스 제거 (-> AS-IS)
.\.docs\round5\benchmark\run-benchmark.ps1 -Action create-index  # 인덱스 생성 (-> TO-BE)
.\.docs\round5\benchmark\run-benchmark.ps1 -Action explain       # 현재 상태로 EXPLAIN ANALYZE
.\.docs\round5\benchmark\run-benchmark.ps1 -Action tiebreak      # price ASC의 id DESC vs id ASC 비교
.\.docs\round5\benchmark\run-benchmark.ps1 -Action status        # 행 수 + 현재 인덱스 목록

# 행 수 조절
.\.docs\round5\benchmark\run-benchmark.ps1 -Action seed -Rows 500000
```

세션 안에서 직접 돌리려면 프롬프트에 `! ` 를 붙인다: `! .\.docs\round5\benchmark\run-benchmark.ps1 -Action explain`

## EXPLAIN ANALYZE 읽는 법

| 줄 | 의미 | 판단 |
|---|---|---|
| `Table scan on product` | 테이블 전수 스캔 | ❌ 인덱스 못 씀 |
| `Index lookup / Index scan using ...` | 인덱스로 접근 | ✅ |
| `Sort: ...` | filesort(별도 정렬 연산) | ❌ 정렬 비용 |
| `(reverse)` | 인덱스 역방향 읽기(DESC 대응) | ✅ |
| `actual time=x..y` | 실제 소요(ms), 맨 바깥 줄이 총합 | 작을수록 좋음 |

## 주의

- `all` / `create-index` 는 끝나면 인덱스가 **생성된 상태**로 둔다(코드 `ProductModel`의 `@Index`와 일치).
- `drop-index` 만 단독 실행하면 인덱스가 빠진 채로 남으니, 측정 후 `create-index` 로 되돌린다.
- `-Action all` 은 매번 **TRUNCATE 후 재시딩**한다 — 기존 product 데이터가 사라진다.

## 파일

| 파일 | 용도 |
|---|---|
| `run-benchmark.ps1` | 러너(시딩/인덱스/EXPLAIN) |
| `01_seed.sql` | 시딩 SQL 단독 참조본 |
| `00_decisions.md` | 의사결정 맥락(왜) |
| `03_benchmark_report.md` | 측정 데이터(AS-IS/TO-BE) |
