# PG 회복력 부하/관측 데모 (Round 6)

서킷브레이커 생애주기(CLOSED → OPEN → HALF_OPEN → CLOSED)와 장애 중 결제의 복구를
**k6 부하 + Prometheus/Grafana**로 눈으로 확인하는 재현 절차.

## 구성
- `payment-load.js` — ramping-arrival-rate(최대 120/s). 단발 부하/서킷 개방 관찰용.
- `payment-steady.js` — constant-arrival-rate. env로 조절: `RATE`(기본 25), `MAXORDER`(기본 50), `DUR`(기본 70s). 생애주기 단계별 부하용.
- Grafana 대시보드(`docker/grafana/provisioning/dashboards/pg-resilience.json`)가 자동 프로비저닝됨. 패널: 서킷 상태 / 실패율·느린호출율 / 결제 p95·p99 / 호출 by kind.

## 사전 준비
```bash
docker compose -f docker/infra-compose.yml up -d        # MySQL/Redis/Kafka
docker compose -f docker/monitoring-compose.yml up -d   # Prometheus(9090) + Grafana(3000, admin/admin)
./gradlew :apps:pg-simulator:bootRun                    # 8082
./gradlew :apps:commerce-api:bootRun                    # 8080 (actuator 8081)
winget install GrafanaLabs.k6                           # k6 설치
```
시드: 유저(`POST /api/v1/users`) + 브랜드/상품/재고 + CREATED 주문 다수.
(결제 API는 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 인증, body `{orderId, cardType, cardNo}`.)

## 생애주기 시나리오
```bash
# 1) CLOSED  — PG 정상 + 부하
k6 run -e MAXORDER=1050 -e RATE=25 payment-steady.js

# 2) OPEN    — 모의 장애: pg-simulator(8082) 프로세스 종료 후 부하
k6 run -e MAXORDER=1050 -e RATE=25 payment-steady.js

# 3) HALF_OPEN -> CLOSED — pg-simulator 재기동 후 저부하
k6 run -e MAXORDER=1050 -e RATE=6 -e DUR=110s payment-steady.js
```

## 관측
- Grafana: http://localhost:3000/d/pg-resilience (Last 15 minutes)
- 서킷 상태 폴링: `GET http://localhost:8081/actuator/prometheus` → `resilience4j_circuitbreaker_state`
- 정합성 검증(결제↔주문):
  ```sql
  SELECT p.status, o.status, COUNT(*) FROM payment p JOIN orders o ON o.id=p.order_id GROUP BY 1,2;
  ```
  기대: SUCCESS↔PAID, FAILED↔CANCELED, PENDING↔CREATED (불일치 0).

> 참고: pg-simulator는 재기동 시 발급했던 거래키 상태를 잃는다. 따라서 PG 정상 구간에서 거래키를
> 받은 PENDING은 재기동 후 조회가 안 돼 PENDING으로 남을 수 있다(시뮬레이터 한계, 실제 PG면 조회로 확정).
