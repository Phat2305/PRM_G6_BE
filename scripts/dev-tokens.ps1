<#
.SYNOPSIS
    Sinh JWT token (HS256) trên bộ nhớ từ biến môi trường $env:JWT_SECRET và chỉ in ra màn hình console.
    Tuyệt đối KHÔNG ghi token ra bất kỳ file nào.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Trim().Length -eq 0) {
    throw "Thiếu biến môi trường `$env:JWT_SECRET. Vui lòng đặt `$env:JWT_SECRET (tối thiểu 32 bytes) trước khi chạy."
}

$SecretBytes = [System.Text.Encoding]::UTF8.GetBytes($env:JWT_SECRET)
if ($SecretBytes.Length -lt 32) {
    throw "JWT_SECRET phải có độ dài tối thiểu 32 bytes (256 bits) cho thuật toán HS256."
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-DevJwt([string]$UserId, [string]$Role) {
    $HeaderJson = '{"alg":"HS256","typ":"JWT"}'
    $Now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $Exp = $Now + 86400
    $PayloadJson = "{`"sub`":`"$UserId`",`"userId`":`"$UserId`",`"role`":`"$Role`",`"type`":`"ACCESS`",`"iat`":$Now,`"exp`":$Exp}"

    $HeaderB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($HeaderJson))
    $PayloadB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($PayloadJson))
    $SigningInput = "$HeaderB64.$PayloadB64"

    $Hmac = [System.Security.Cryptography.HMACSHA256]::new($SecretBytes)
    try {
        $SigBytes = $Hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($SigningInput))
    } finally {
        $Hmac.Dispose()
    }
    $SigB64 = ConvertTo-Base64Url $SigBytes
    return "$SigningInput.$SigB64"
}

$Roles = @(
    @{ Role = "PARENT";  UserId = "11111111-1111-1111-1111-111111111111" },
    @{ Role = "TEACHER"; UserId = "22222222-2222-2222-2222-222222222222" },
    @{ Role = "STAFF";   UserId = "33333333-3333-3333-3333-333333333333" },
    @{ Role = "ADMIN";   UserId = "44444444-4444-4444-4444-444444444444" },
    @{ Role = "CHILD";   UserId = "55555555-5555-5555-5555-555555555555" }
)

Write-Host "=== DEV JWT TOKENS (IN-MEMORY ONLY, NOT SAVED TO DISK) ===" -ForegroundColor Cyan
foreach ($item in $Roles) {
    $token = New-DevJwt -UserId $item.UserId -Role $item.Role
    Write-Host ("[{0}] (userId={1}):`nBearer {2}`n" -f $item.Role, $item.UserId, $token)
}
