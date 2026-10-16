<#
.SYNOPSIS
    Script khởi tạo lại cơ sở dữ liệu môi trường phát triển (DEV) cho nhóm PRM_G6_BE.

.DESCRIPTION
    Script thực hiện:
    1. Kiểm tra an toàn bất biến:
       - Tên cơ sở dữ liệu BẮT BUỘC là 'art_course_dev' (từ chối mọi tên khác, đặc biệt 'art_course').
       - Host chỉ chấp nhận localhost, 127.0.0.1 hoặc ::1.
       - Quét file docs/ART_COUSE.sql và V1__be2_schema_init.sql để đảm bảo không có \connect, \c, CREATE DATABASE, DROP DATABASE, ALTER DATABASE.
    2. Ngắt kết nối và DROP DATABASE IF EXISTS art_course_dev; CREATE DATABASE art_course_dev;
    3. Nạp schema cơ sở từ docs/ART_COUSE.sql với cờ -v ON_ERROR_STOP=1.
    4. Không dung thứ bất kỳ lỗi nào ($LASTEXITCODE phải = 0). Dừng ngay lập tức nếu gặp lỗi.
    5. Không nạp V1 bằng tay, không tạo giả flyway_schema_history: Việc migrate V1 sẽ do Flyway thực hiện khi ứng dụng chạy ở profile dev.
    6. Mật khẩu không hard-code; tự động dọn dẹp $env:PGPASSWORD trong khối finally.

.PARAMETER TargetDb
    Tên database mục tiêu. BẮT BUỘC là 'art_course_dev'.

.PARAMETER PgHost
    PostgreSQL Host (chỉ chấp nhận: localhost, 127.0.0.1, ::1). Mặc định: localhost.

.PARAMETER PgPort
    PostgreSQL Port. Mặc định: 5432.

.PARAMETER PgUser
    PostgreSQL User. Mặc định: postgres.

.PARAMETER DockerContainer
    Tên container Docker chứa PostgreSQL. Mặc định: 'pg-artcourse'.
#>

[CmdletBinding()]
param(
    [string]$TargetDb = "art_course_dev",
    [string]$PgHost = "localhost",
    [int]$PgPort = 5432,
    [string]$PgUser = "postgres",
    [string]$DockerContainer = "pg-artcourse"
)

# Biến cờ theo dõi thành công
$ScriptSucceeded = $false

try {
    Write-Host "=================================================================" -ForegroundColor Cyan
    Write-Host "      SCRIPT RESET CƠ SỞ DỮ LIỆU PHÁT TRIỂN (DEV ONLY)" -ForegroundColor Cyan
    Write-Host "=================================================================" -ForegroundColor Cyan

    # -------------------------------------------------------------------------
    # 1. BẢO VỆ AN TOÀN BẤT BIẾN (SAFETY GUARDS)
    # -------------------------------------------------------------------------
    # Guard 1: Tên Database
    if ($TargetDb -ne "art_course_dev") {
        Write-Host "[LỖI NGUY HIỂM] Tên database được chỉ định là: '$TargetDb'" -ForegroundColor Red
        Write-Host "[TỪ CHỐI THỰC THI] Script này CHỈ được phép thao tác trên duy nhất 'art_course_dev'!" -ForegroundColor Red
        Write-Host "[CẢNH BÁO] Nghiêm cấm mọi hành vi DROP hoặc RESET trên database 'art_course' hoặc production!" -ForegroundColor Red
        throw "Safety Guard Abort: Target database must be strictly 'art_course_dev'."
    }

    # Guard 2: Host chỉ chấp nhận loopback
    $AllowedHosts = @("localhost", "127.0.0.1", "::1")
    if ($AllowedHosts -notcontains $PgHost.ToLower()) {
        Write-Host "[LỖI AN TOÀN] PgHost '$PgHost' không thuộc danh sách cho phép (localhost, 127.0.0.1, ::1)!" -ForegroundColor Red
        throw "Safety Guard Abort: PgHost must be strictly localhost, 127.0.0.1 or ::1."
    }

    # Xác định đường dẫn file
    $ProjectRoot = Split-Path -Parent $PSScriptRoot
    $BaseSchemaFile = Join-Path $ProjectRoot "docs\ART_COUSE.sql"
    $MigrationV1File = Join-Path $ProjectRoot "src\main\resources\db\migration\V1__be2_schema_init.sql"

    if (-not (Test-Path $BaseSchemaFile)) {
        throw "Không tìm thấy file schema cơ sở: $BaseSchemaFile"
    }
    if (-not (Test-Path $MigrationV1File)) {
        throw "Không tìm thấy file migration V1: $MigrationV1File"
    }

    # Guard 3: Quét kiểm tra mã độc hại trong các file SQL
    Write-Host "[KIỂM TRA] Đang quét nội dung SQL để ngăn chặn lệnh can thiệp nguy hiểm..." -ForegroundColor Yellow
    $DangerousPattern = '(?i)(\\connect|\b\\c\s+|CREATE\s+DATABASE|DROP\s+DATABASE|ALTER\s+DATABASE)'
    
    $BaseSchemaDangerous = Select-String -Path $BaseSchemaFile -Pattern $DangerousPattern
    if ($BaseSchemaDangerous) {
        Write-Host "[LỖI AN TOÀN] Phát hiện lệnh nguy hiểm trong $BaseSchemaFile`: $($BaseSchemaDangerous[0].Line)" -ForegroundColor Red
        throw "Safety Guard Abort: Dangerous SQL command detected in base schema."
    }

    $V1Dangerous = Select-String -Path $MigrationV1File -Pattern $DangerousPattern
    if ($V1Dangerous) {
        Write-Host "[LỖI AN TOÀN] Phát hiện lệnh nguy hiểm trong $MigrationV1File`: $($V1Dangerous[0].Line)" -ForegroundColor Red
        throw "Safety Guard Abort: Dangerous SQL command detected in migration V1."
    }
    Write-Host "[HỢP LỆ] File SQL an toàn, không chứa lệnh chuyển database hay phá hủy hệ thống." -ForegroundColor Green

    # Lấy mật khẩu: từ biến môi trường hoặc hỏi lúc chạy
    if (-not $env:PGPASSWORD) {
        if ($env:SPRING_DATASOURCE_PASSWORD) {
            $env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
        } else {
            $SecurePass = Read-Host "Nhập mật khẩu PostgreSQL cho user '$PgUser' (hoặc đặt biến `$env:PGPASSWORD`)" -AsSecureString
            $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePass)
            $env:PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
        }
    }

    # Xác định cơ chế thực thi: Ưu tiên psql trong PATH, nếu không có thì dùng Docker
    $hasPsql = Get-Command psql -ErrorAction SilentlyContinue

    if ($hasPsql) {
        Write-Host "`n>>> [PHƯƠNG THỨC: LOCAL PSQL]" -ForegroundColor Cyan
        Write-Host "[BƯỚC 1/2] Ngắt kết nối và tạo mới database '$TargetDb'..." -ForegroundColor Yellow
        
        $ResetSql = @"
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE datname = '$TargetDb' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS $TargetDb;
CREATE DATABASE $TargetDb WITH ENCODING = 'UTF8';
"@
        $ResetSql | psql -h $PgHost -p $PgPort -U $PgUser -d postgres -v ON_ERROR_STOP=1
        if ($LASTEXITCODE -ne 0) {
            throw "Lỗi khi DROP/CREATE database '$TargetDb' bằng psql (Exit code: $LASTEXITCODE)."
        }

        Write-Host "`n[BƯỚC 2/2] Nạp schema cơ sở từ docs/ART_COUSE.sql..." -ForegroundColor Yellow
        psql -h $PgHost -p $PgPort -U $PgUser -d $TargetDb -v ON_ERROR_STOP=1 -f $BaseSchemaFile
        if ($LASTEXITCODE -ne 0) {
            throw "Lỗi khi nạp docs/ART_COUSE.sql vào database '$TargetDb' (Exit code: $LASTEXITCODE). Dừng ngay lập tức!"
        }

    } else {
        Write-Host "`n>>> [PHƯƠNG THỨC: DOCKER CONTAINER]" -ForegroundColor Cyan
        
        # Kiểm tra Docker CLI và kiểm tra container đang chạy
        $hasDocker = Get-Command docker -ErrorAction SilentlyContinue
        if (-not $hasDocker) {
            throw "Không tìm thấy cả 'psql' lẫn 'docker' trong hệ thống. Vui lòng cài đặt PostgreSQL client hoặc khởi động Docker."
        }

        Write-Host "[KIỂM TRA DOCKER] Kiểm tra container '$DockerContainer'..." -ForegroundColor Yellow
        $runningContainer = docker ps --filter "name=^/${DockerContainer}$" --filter "name=^${DockerContainer}$" --format "{{.Names}}"
        if (-not $runningContainer) {
            throw "Container Docker '$DockerContainer' hiện không chạy hoặc không tồn tại. Vui lòng kiểm tra lệnh 'docker ps'."
        }
        Write-Host "[XÁC NHẬN] Container '$DockerContainer' đang hoạt động." -ForegroundColor Green

        Write-Host "`n[BƯỚC 1/2] Ngắt kết nối và tạo mới database '$TargetDb' qua container..." -ForegroundColor Yellow
        $TerminateAndCreateCmd = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TargetDb' AND pid <> pg_backend_pid(); DROP DATABASE IF EXISTS $TargetDb; CREATE DATABASE $TargetDb WITH ENCODING = 'UTF8';"
        
        docker exec -e PGPASSWORD=$env:PGPASSWORD $DockerContainer psql -U $PgUser -d postgres -v ON_ERROR_STOP=1 -c $TerminateAndCreateCmd
        if ($LASTEXITCODE -ne 0) {
            throw "Lỗi khi DROP/CREATE database '$TargetDb' qua Docker (Exit code: $LASTEXITCODE)."
        }

        Write-Host "`n[BƯỚC 2/2] Chép docs/ART_COUSE.sql vào container và nạp schema cơ sở..." -ForegroundColor Yellow
        docker cp $BaseSchemaFile "${DockerContainer}:/tmp/ART_COUSE.sql"
        if ($LASTEXITCODE -ne 0) {
            throw "Lỗi khi copy file docs/ART_COUSE.sql vào container (Exit code: $LASTEXITCODE)."
        }

        docker exec -e PGPASSWORD=$env:PGPASSWORD $DockerContainer psql -U $PgUser -d $TargetDb -v ON_ERROR_STOP=1 -f /tmp/ART_COUSE.sql
        $napExitCode = $LASTEXITCODE

        # Dọn dẹp file tạm trong container
        docker exec $DockerContainer rm -f /tmp/ART_COUSE.sql

        if ($napExitCode -ne 0) {
            throw "Lỗi khi nạp /tmp/ART_COUSE.sql vào database '$TargetDb' (Exit code: $napExitCode). Dừng ngay lập tức!"
        }
    }

    $ScriptSucceeded = $true
    Write-Host "`n=================================================================" -ForegroundColor Green
    Write-Host " [THÀNH CÔNG] Cơ sở dữ liệu dev '$TargetDb' đã được tái lập sạch sẽ!" -ForegroundColor Green
    Write-Host " Migration V1 sẽ do Flyway tự động áp dụng khi khởi động profile dev." -ForegroundColor Green
    Write-Host " Chuỗi kết nối DEV:" -ForegroundColor Green
    Write-Host " DATABASE_URL_DEV=jdbc:postgresql://$PgHost`:$PgPort/$TargetDb" -ForegroundColor Cyan
    Write-Host "=================================================================" -ForegroundColor Green

} finally {
    # -------------------------------------------------------------------------
    # DỌN DẸP BẢO MẬT: XÓA BIẾN MÔI TRƯỜNG PGPASSWORD
    # -------------------------------------------------------------------------
    if ($env:PGPASSWORD) {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
    if (-not $ScriptSucceeded) {
        Write-Host "`n[KẾT QUẢ] Quá trình reset database DEV THẤT BẠI hoặc ĐÃ BỊ HỦY. Database chưa sẵn sàng." -ForegroundColor Red
    }
}
