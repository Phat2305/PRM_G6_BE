<#
.SYNOPSIS
    Khởi tạo lại schema môi trường DEV (art_course_dev) trong container pg-artcourse bằng tài khoản dev_agent.
    Tuyệt đối không dùng tài khoản postgres, không kết nối art_course, không đọc .env.
#>
[CmdletBinding()]
param(
    [string]$Container = "pg-artcourse",
    [string]$DbName = "art_course_dev",
    [string]$DbUser = "dev_agent"
)

$ErrorActionPreference = "Stop"

if ($DbName -ne "art_course_dev") {
    throw "SAFETY GUARD: Chỉ cho phép thao tác trên database 'art_course_dev' (nhận được: '$DbName')."
}
if ($DbUser -ne "dev_agent") {
    throw "SAFETY GUARD: Chỉ cho phép dùng tài khoản 'dev_agent' (nhận được: '$DbUser')."
}
if (-not $env:DEV_DB_PASSWORD) {
    throw "Thiếu biến môi trường `$env:DEV_DB_PASSWORD."
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BaseSql = Join-Path $ProjectRoot "docs\ART_COUSE.sql"

Write-Host "[1/2] Sao chép docs/ART_COUSE.sql vào bản sao tạm /tmp/ART_COUSE_copy.sql trong container..." -ForegroundColor Cyan
docker cp $BaseSql "${Container}:/tmp/ART_COUSE_copy.sql"

Write-Host "[2/2] Dọn sạch schema public và nạp bản sao nền đã lọc (SET transaction_timeout, OWNER TO)..." -ForegroundColor Cyan
docker exec -e PGPASSWORD=$env:DEV_DB_PASSWORD $Container psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 `
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" `
    -c "\! sed -i -e '/^SET transaction_timeout/d' -e '/OWNER TO/d' /tmp/ART_COUSE_copy.sql" `
    -f /tmp/ART_COUSE_copy.sql

if ($LASTEXITCODE -ne 0) {
    throw "Nạp schema nền vào art_course_dev thất bại (Exit code: $LASTEXITCODE)."
}

Write-Host "[OK] art_course_dev đã sẵn sàng với schema nền sạch. Khởi chạy app với --spring.profiles.active=dev để Flyway áp V1 & V2." -ForegroundColor Green
