#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('seed', 'drop-index', 'create-index', 'explain', 'tiebreak', 'status', 'all')]
    [string]$Action = 'all',
    [int]$Rows = 100000,
    [string]$Container = 'docker-mysql-1',
    [string]$Database = 'loopers',
    [string]$DatabaseUser = 'application',
    [string]$DatabasePwd = 'application'
)

# 네이티브 mysql stderr('Using a password' 경고)가 종료 오류가 되지 않도록 Continue 유지.
$ErrorActionPreference = 'Continue'

$Indexes = [ordered]@{
    'idx_product_brand_like'    = 'brand_id, like_count'
    'idx_product_brand_price'   = 'brand_id, price'
    'idx_product_brand_created' = 'brand_id, created_at'
    'idx_product_like'          = 'like_count'
}

function Invoke-Sql([string]$Sql) {
    $uArg = "-u$DatabaseUser"
    $pArg = "-p$DatabasePwd"
    docker exec $Container mysql $uArg $pArg $Database -e $Sql 2>&1 |
        Where-Object { $_ -notmatch 'Using a password' }
}

function Get-ExistingIndexes {
    $uArg = "-u$DatabaseUser"
    $pArg = "-p$DatabasePwd"
    $sql = "SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema='$Database' AND table_name='product' AND index_name <> 'PRIMARY';"
    $names = docker exec $Container mysql $uArg $pArg $Database -N -e $sql 2>$null
    return @($names | Where-Object { $_ -and $_.Trim() })
}

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Do-Seed {
    Write-Section "Seed $Rows products (truncate first)"
    $sql = @"
SET SESSION cte_max_recursion_depth = $($Rows + 1000);
TRUNCATE TABLE product;
INSERT INTO product (brand_id, name, description, price, like_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < $Rows
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
"@
    $start = Get-Date
    Invoke-Sql $sql
    $sec = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
    Invoke-Sql "SELECT COUNT(*) AS rows_total, COUNT(DISTINCT brand_id) AS brands, SUM(deleted_at IS NOT NULL) AS deleted FROM product;"
    Write-Host "seeded in ${sec}s" -ForegroundColor Green
}

function Do-DropIndex {
    Write-Section 'Drop indexes (-> AS-IS)'
    $existing = Get-ExistingIndexes
    foreach ($name in $Indexes.Keys) {
        if ($existing -contains $name) {
            Invoke-Sql "DROP INDEX $name ON product;"
            Write-Host "dropped $name"
        } else {
            Write-Host "skip   $name (absent)"
        }
    }
}

function Do-CreateIndex {
    Write-Section 'Create indexes (-> TO-BE)'
    $existing = Get-ExistingIndexes
    foreach ($name in $Indexes.Keys) {
        if ($existing -contains $name) {
            Write-Host "skip   $name (exists)"
        } else {
            Invoke-Sql "CREATE INDEX $name ON product ($($Indexes[$name]));"
            Write-Host "created $name ($($Indexes[$name]))"
        }
    }
}

function Do-Explain {
    Write-Section 'EXPLAIN ANALYZE — list queries (current index state)'
    $queries = [ordered]@{
        'brand=1 + like desc'  = "SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL ORDER BY like_count DESC, id DESC LIMIT 20"
        'brand=1 + price asc'  = "SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL ORDER BY price ASC, id ASC LIMIT 20"
        'brand=1 + latest'     = "SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL ORDER BY created_at DESC, id DESC LIMIT 20"
        'global + like desc'   = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY like_count DESC, id DESC LIMIT 20"
    }
    foreach ($label in $queries.Keys) {
        Write-Host ''
        Write-Host "-- $label" -ForegroundColor Yellow
        Invoke-Sql "EXPLAIN ANALYZE $($queries[$label])\G"
    }
}

function Do-Tiebreak {
    Write-Section 'tiebreak direction: price ASC + id DESC (mismatch) vs id ASC (match)'
    Write-Host ''
    Write-Host '-- (A) price ASC, id DESC  -> filesort 잔존 예상' -ForegroundColor Yellow
    Invoke-Sql "EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL ORDER BY price ASC, id DESC LIMIT 20\G"
    Write-Host ''
    Write-Host '-- (B) price ASC, id ASC   -> filesort 제거 예상' -ForegroundColor Yellow
    Invoke-Sql "EXPLAIN ANALYZE SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL ORDER BY price ASC, id ASC LIMIT 20\G"
}

function Do-Status {
    Write-Section 'status'
    Invoke-Sql "SELECT COUNT(*) AS product_rows FROM product;"
    Write-Host 'indexes:'
    Get-ExistingIndexes | ForEach-Object { Write-Host "  $_" }
}

switch ($Action) {
    'seed'         { Do-Seed }
    'drop-index'   { Do-DropIndex }
    'create-index' { Do-CreateIndex }
    'explain'      { Do-Explain }
    'tiebreak'     { Do-Tiebreak }
    'status'       { Do-Status }
    'all' {
        Do-Seed
        Do-DropIndex
        Write-Host ''
        Write-Host '### AS-IS (no index) ###' -ForegroundColor Magenta
        Do-Explain
        Do-CreateIndex
        Write-Host ''
        Write-Host '### TO-BE (with index) ###' -ForegroundColor Magenta
        Do-Explain
        Do-Tiebreak
        Write-Host ''
        Write-Host 'NOTE: 인덱스가 생성된 상태로 종료됨 (코드의 @Index와 일치).' -ForegroundColor Green
    }
}
