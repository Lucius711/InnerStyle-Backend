<#
.SYNOPSIS
  Expose your local InnerStyle app to the internet (for VNPay/MoMo callbacks) and write the
  public URLs into .env automatically. Uses Cloudflare Tunnel (cloudflared) — no account needed.

.DESCRIPTION
  - If you run the app with docker compose (nginx on port 80), use -Port 80 (default): one tunnel
    serves both the SPA and the API.
  - If you run backend separately (Spring Boot on 2207), use -Port 2207 (the return page will be
    served by the backend tunnel too — for split dev prefer docker).

  After it prints the URL, register this IPN URL in the VNPay portal:
      <public-url>/api/common/payments/vnpay/ipn

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1
  powershell -ExecutionPolicy Bypass -File scripts\tunnel.ps1 -Port 2207
#>
param(
  [int]$Port = 80,
  [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

function Ensure-Cloudflared {
  if (Get-Command cloudflared -ErrorAction SilentlyContinue) { return "cloudflared" }
  Write-Host "cloudflared not found — installing..." -ForegroundColor Yellow
  if (Get-Command winget -ErrorAction SilentlyContinue) {
    winget install --id Cloudflare.cloudflared -e --accept-source-agreements --accept-package-agreements | Out-Null
    if (Get-Command cloudflared -ErrorAction SilentlyContinue) { return "cloudflared" }
  }
  # Fallback: download the standalone exe.
  $dest = Join-Path $env:TEMP "cloudflared.exe"
  $url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
  Write-Host "Downloading cloudflared from GitHub..." -ForegroundColor Yellow
  Invoke-WebRequest -Uri $url -OutFile $dest
  return $dest
}

function Update-EnvFile([string]$path, [hashtable]$values) {
  if (-not (Test-Path $path)) { New-Item -ItemType File -Path $path | Out-Null }
  $lines = Get-Content $path
  foreach ($key in $values.Keys) {
    $line = "$key=$($values[$key])"
    if ($lines -match "^\s*$key=") {
      $lines = $lines -replace "^\s*$key=.*", $line
    } else {
      $lines += $line
    }
  }
  Set-Content -Path $path -Value $lines -Encoding UTF8
}

$cf = Ensure-Cloudflared
$log = Join-Path $env:TEMP "cloudflared-innerstyle.log"
if (Test-Path $log) { Remove-Item $log -Force }

Write-Host "Starting Cloudflare tunnel -> http://localhost:$Port ..." -ForegroundColor Cyan
$proc = Start-Process -FilePath $cf `
  -ArgumentList "tunnel --url http://localhost:$Port --logfile `"$log`"" `
  -PassThru -WindowStyle Hidden

try {
  $publicUrl = $null
  for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $log) {
      $m = Select-String -Path $log -Pattern "https://[a-z0-9-]+\.trycloudflare\.com" -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($m) { $publicUrl = $m.Matches[0].Value; break }
    }
  }
  if (-not $publicUrl) {
    throw "Could not detect the tunnel URL. Check $log"
  }

  Write-Host ""
  Write-Host "Public URL: $publicUrl" -ForegroundColor Green

  Update-EnvFile -path $EnvFile -values @{
    "FRONTEND_BASE_URL" = $publicUrl
    "VNPAY_RETURN_URL"  = "$publicUrl/wallet/vnpay-return"
    "MOMO_REDIRECT_URL" = "$publicUrl/wallet/momo-return"
    "MOMO_IPN_URL"      = "$publicUrl/api/common/payments/momo/ipn"
  }

  Write-Host ""
  Write-Host "Updated $EnvFile. Now:" -ForegroundColor Cyan
  Write-Host "  1) Register this VNPay IPN URL in the VNPay portal:" -ForegroundColor White
  Write-Host "       $publicUrl/api/common/payments/vnpay/ipn" -ForegroundColor Yellow
  Write-Host "  2) Restart the backend so it picks up the new URLs:" -ForegroundColor White
  Write-Host "       docker compose up -d backend        (docker mode)" -ForegroundColor Gray
  Write-Host "       or restart 'mvn spring-boot:run'    (local mode)" -ForegroundColor Gray
  Write-Host ""
  Write-Host "Keep this window open — closing it stops the tunnel. Press Ctrl+C to stop." -ForegroundColor Cyan
  Wait-Process -Id $proc.Id
}
finally {
  if ($proc -and -not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Write-Host "Tunnel stopped." -ForegroundColor DarkGray
  }
}
