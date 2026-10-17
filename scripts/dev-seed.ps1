<#
.SYNOPSIS
    Nạp dữ liệu mẫu (idempotent) vào database art_course_dev bằng tài khoản dev_agent.
#>
[CmdletBinding()]
param(
    [string]$Container = "pg-artcourse",
    [string]$DbName = "art_course_dev",
    [string]$DbUser = "dev_agent"
)

$ErrorActionPreference = "Stop"

if ($DbName -ne "art_course_dev" -or $DbUser -ne "dev_agent") {
    throw "SAFETY GUARD: Chỉ được phép chạy với user 'dev_agent' trên database 'art_course_dev'."
}
if (-not $env:DEV_DB_PASSWORD) {
    throw "Thiếu biến môi trường `$env:DEV_DB_PASSWORD."
}

$SeedSql = @"
BEGIN;
INSERT INTO public.users (id, username, password, role, status) VALUES
  ('11111111-1111-1111-1111-111111111111', 'parent_dev', 'hashed_pw', 'PARENT', 'ACTIVE'),
  ('22222222-2222-2222-2222-222222222222', 'teacher_dev', 'hashed_pw', 'TEACHER', 'ACTIVE'),
  ('33333333-3333-3333-3333-333333333333', 'staff_dev', 'hashed_pw', 'STAFF', 'ACTIVE'),
  ('44444444-4444-4444-4444-444444444444', 'admin_dev', 'hashed_pw', 'ADMIN', 'ACTIVE'),
  ('55555555-5555-5555-5555-555555555555', 'child_dev', 'hashed_pw', 'CHILD', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.parent_profiles (user_id, full_name, shipping_address) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Phu Huynh Dev', '123 Nguyen Hue, Q1, TP.HCM')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO public.teacher_profiles (user_id, full_name, certificates_text, bank_account_info) VALUES
  ('22222222-2222-2222-2222-222222222222', 'Giao Vien Dev', 'Cu nhan My Thuat', 'VCB - 0123456789 - GIAO VIEN DEV')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO public.child_profiles (user_id, parent_id, display_name, birth_year) VALUES
  ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Be Vung Ve', 2017)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO public.courses (id, teacher_id, name, description, category, price, status) VALUES
  ('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'Khoa hoc Mau Nuoc Co Ban', 'Khoa hoc cho be', 'WATERCOLOR', 300000, 'PUBLISHED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.supplies (id, teacher_id, name, description, price, stock_quantity, reserved_quantity, sold_quantity, status) VALUES
  ('77777777-7777-7777-7777-777777777777', '22222222-2222-2222-2222-222222222222', 'Bo Co Ve 12 Cay', 'Co ve long mem', 120000, 20, 0, 0, 'PUBLISHED')
ON CONFLICT (id) DO NOTHING;
COMMIT;
"@

docker exec -e PGPASSWORD=$env:DEV_DB_PASSWORD $Container psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c $SeedSql
if ($LASTEXITCODE -ne 0) {
    throw "Seed dữ liệu mẫu vào art_course_dev thất bại."
}
Write-Host "[OK] Đã nạp dữ liệu mẫu (idempotent) vào art_course_dev." -ForegroundColor Green
