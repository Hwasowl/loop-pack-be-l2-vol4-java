SET SESSION cte_max_recursion_depth = 200000;

INSERT INTO product (brand_id, name, description, price, like_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
  FLOOR(RAND() * 100) + 1,
  CONCAT('product-', n),
  CONCAT('description for product ', n),
  FLOOR(RAND() * 1999000) + 1000,
  FLOOR(POW(RAND(), 3) * 100000),
  NOW(6) - INTERVAL FLOOR(RAND() * 730) DAY,
  NOW(6),
  CASE WHEN RAND() < 0.02 THEN NOW(6) - INTERVAL FLOOR(RAND() * 100) DAY ELSE NULL END
FROM seq;
