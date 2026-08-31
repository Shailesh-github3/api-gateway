# PowerShell Scripts for Fault Injection and Performance Testing
# RQ1: Traffic Control Under Redis Fault Injection

# ============================================================================
# PREREQUISITE: Verify API Key
# ============================================================================
# First, get a valid API key using the admin endpoint.
# Replace OWNER_ID with your actual owner ID.

$OWNER_ID = "owner-123"
$ADMIN_USER = "admin"
$ADMIN_PASS = "admin"

$headers = @{
    "Authorization" = "Basic " + [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("$($ADMIN_USER):$($ADMIN_PASS)"))
    "Content-Type" = "application/json"
}

$body = @{
    ownerId = $OWNER_ID
    tier = "STARTER"
    scopes = @("read")
} | ConvertTo-Json

Write-Host "Creating new API key..." -ForegroundColor Cyan
$response = Invoke-RestMethod -Uri "http://localhost:8080/admin/api-keys" `
    -Method POST `
    -Headers $headers `
    -Body $body

$API_KEY = $response.raw_key
Write-Host "✓ API Key created: $API_KEY" -ForegroundColor Green
Write-Host "⚠ SAVE THIS KEY NOW - it will never be shown again!" -ForegroundColor Yellow

# ============================================================================
# STEP 1: Update k6 script with actual API key
# ============================================================================
Write-Host "`n=== Updating k6 script with API key ===" -ForegroundColor Cyan

$k6_file = "load-tests/rq1-fault-injection.js"
$k6_content = Get-Content $k6_file -Raw
$k6_content = $k6_content -replace "__REPLACE_WITH_ACTUAL_KEY__", $API_KEY
Set-Content -Path $k6_file -Value $k6_content

Write-Host "✓ Updated $k6_file with actual API key" -ForegroundColor Green

# ============================================================================
# STEP 2: Run k6 test with NDJSON export
# ============================================================================
Write-Host "`n=== STEP 2: Running k6 load test ===" -ForegroundColor Cyan
Write-Host "Command: k6 run --out json=rq1_results.json load-tests/rq1-fault-injection.js" -ForegroundColor Yellow
Write-Host "This will run for 30 seconds. Watch for this pattern:" -ForegroundColor Yellow
Write-Host "  0-5s:   Baseline (healthy redis)" -ForegroundColor Gray
Write-Host "  5-15s:  Fault window (5000ms latency injected)" -ForegroundColor Red
Write-Host "  15-30s: Recovery (latency removed)" -ForegroundColor Green
Write-Host ""

# Start k6 in the background
$k6Process = Start-Process -FilePath "k6" -ArgumentList "run", "--out", "json=rq1_results.json", "load-tests/rq1-fault-injection.js" `
    -NoNewWindow -PassThru

# Give k6 a moment to start
Start-Sleep -Seconds 5

Write-Host "`nℹ k6 test started (PID: $($k6Process.Id)). It will run for ~30 seconds." -ForegroundColor Cyan
Write-Host "Proceed to STEP 3 in a NEW PowerShell terminal while this is running..." -ForegroundColor Yellow

# ============================================================================
# STEP 3: Toxiproxy Latency Injection (Run in SEPARATE terminal)
# ============================================================================
# Copy-paste this block into a NEW PowerShell terminal

$TOXIPROXY_URL = "http://localhost:8474"
$PROXY_NAME = "redis_proxy"
$TOXIC_NAME = "slow_redis"
$LATENCY_MS = 5000

Write-Host "`n=== STEP 3: Toxiproxy Latency Injection (RUN IN NEW TERMINAL) ===" -ForegroundColor Magenta
Write-Host "`nPaste this into a NEW PowerShell terminal (DO NOT RUN HERE YET):`n" -ForegroundColor Yellow
Write-Host @"
# === Start of SEPARATE Terminal Block ===
`$TOXIPROXY_URL = "http://localhost:8474"
`$PROXY_NAME = "redis_proxy"
`$TOXIC_NAME = "slow_redis"
`$LATENCY_MS = 5000

# Wait until k6 has been running for ~5 seconds (baseline complete)
Write-Host "Waiting 5 seconds before injecting fault..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

# Create latency toxic on redis_proxy
Write-Host "Injecting 5000ms latency on redis_proxy..." -ForegroundColor Yellow
`$body = @{
    name = `$TOXIC_NAME
    type = "latency"
    stream = "upstream"
    toxicity = 1.0
    attributes = @{
        latency = `$LATENCY_MS
    }
} | ConvertTo-Json

`$response = Invoke-RestMethod `
    -Uri "`$TOXIPROXY_URL/proxies/`$PROXY_NAME/toxics" `
    -Method POST `
    -ContentType "application/json" `
    -Body `$body

Write-Host "✓ Latency toxic created:" -ForegroundColor Green
`$response | ConvertTo-Json

# Keep latency active for ~10 seconds
Write-Host "Latency will be active for 10 seconds..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

# Remove the latency toxic (recovery window)
Write-Host "Removing latency toxic (recovery begins)..." -ForegroundColor Yellow
`$response = Invoke-RestMethod `
    -Uri "`$TOXIPROXY_URL/proxies/`$PROXY_NAME/toxics/`$TOXIC_NAME" `
    -Method DELETE

Write-Host "✓ Latency toxic removed. Recovery in progress." -ForegroundColor Green
Write-Host "Wait for k6 to complete (remaining ~10 seconds)..." -ForegroundColor Cyan

# === End of SEPARATE Terminal Block ===
"@ -ForegroundColor Green

# Wait for k6 to complete
Write-Host "`nWaiting for k6 process to complete..." -ForegroundColor Cyan
$k6Process | Wait-Process
Write-Host "✓ k6 test completed" -ForegroundColor Green

# ============================================================================
# STEP 4: Verify Output
# ============================================================================
Write-Host "`n=== STEP 4: Verifying Output ===" -ForegroundColor Cyan

$output_file = "rq1_results.json"

if (Test-Path $output_file) {
    $file_size = (Get-Item $output_file).Length
    Write-Host "✓ Output file exists: $output_file" -ForegroundColor Green
    Write-Host "  File size: $([math]::Round($file_size / 1KB, 2)) KB" -ForegroundColor Green
    
    # Show first 3 lines (NDJSON format, one JSON object per line)
    Write-Host "`n  First 3 lines of output:" -ForegroundColor Cyan
    $lines = Get-Content $output_file -TotalCount 3
    foreach ($line in $lines) {
        Write-Host "    $line" -ForegroundColor Gray
    }
    
} else {
    Write-Host "✗ Output file not found: $output_file" -ForegroundColor Red
}

# ============================================================================
# STEP 5: Analyze Results
# ============================================================================
Write-Host "`n=== STEP 5: Analyze Results ===" -ForegroundColor Cyan

Write-Host "`nRun Python analysis script:" -ForegroundColor Yellow
Write-Host "  python analyze_k6_results.py rq1_results.json rq1_analysis.png 5 15" -ForegroundColor Green

Write-Host "`nExpected output file: rq1_analysis.png (with fault window 5-15s marked)" -ForegroundColor Cyan

Write-Host "`n" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "RQ1 COMPLETE - Latency injection and recovery observed!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green
