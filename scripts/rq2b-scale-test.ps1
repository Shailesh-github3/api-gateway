# PowerShell Scripts for RQ2b: Verification Latency at Scale

# ============================================================================
# RQ2b: Verification Latency at Scale
# ============================================================================

# This test measures key verification latency at increasing DB sizes:
# - 100 keys
# - 10,000 keys
# - 1,000,000 keys

# Compares O(1) prefix lookup vs O(N) naive scan approaches.

# Prerequisites:
# - k6 installed (https://k6.io/)
# - Python 3 with pandas installed
# - API endpoint supporting key_count parameter: GET /v1/verify-key?keyCount=N
# - Valid API key

# ============================================================================
# CONFIGURATION
# ============================================================================

$API_KEY = "__REPLACE_WITH_ACTUAL_KEY__"  # From admin endpoint
$API_ENDPOINT = "http://localhost:8080/v1/verify-key"
$OUTPUT_FILE = "rq2b_scale_results.json"

# Key counts to test (in ascending order)
$KEY_COUNTS = @(100, 10000, 1000000)

Write-Host "=== RQ2b: Scale Test (Verification Latency) ===" -ForegroundColor Cyan
Write-Host "Testing with key counts: $($KEY_COUNTS -join ', ')" -ForegroundColor Gray

# ============================================================================
# STEP 1: Verify API Endpoint
# ============================================================================
Write-Host "`n=== STEP 1: Verifying API Endpoint ===" -ForegroundColor Cyan

$headers = @{
    "X-API-Key" = $API_KEY
}

try {
    $response = Invoke-RestMethod -Uri "$API_ENDPOINT?keyCount=100" `
        -Headers $headers `
        -TimeoutSec 10
    
    Write-Host "✓ API endpoint is reachable" -ForegroundColor Green
    Write-Host "  Response: $($response | ConvertTo-Json)" -ForegroundColor Gray
} catch {
    Write-Host "✗ API endpoint not reachable" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "  Ensure the app is running on http://localhost:8080" -ForegroundColor Yellow
    exit 1
}

# ============================================================================
# STEP 2: Update k6 Script with API Key
# ============================================================================
Write-Host "`n=== STEP 2: Preparing k6 Script ===" -ForegroundColor Cyan

$k6_file = "load-tests/rq2b-scale-test.js"
if (Test-Path $k6_file) {
    $k6_content = Get-Content $k6_file -Raw
    $k6_content = $k6_content -replace "__REPLACE_WITH_ACTUAL_KEY__", $API_KEY
    Set-Content -Path $k6_file -Value $k6_content
    Write-Host "✓ Updated k6 script with API key" -ForegroundColor Green
} else {
    Write-Host "✗ k6 script not found: $k6_file" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 3: Run Scale Tests (One test per key count)
# ============================================================================
Write-Host "`n=== STEP 3: Running Scale Tests ===" -ForegroundColor Cyan
Write-Host "Each test runs for 30 seconds with 2 VUs" -ForegroundColor Gray

$all_results = @()

foreach ($key_count in $KEY_COUNTS) {
    Write-Host "`n  Testing with $key_count keys..." -ForegroundColor Yellow
    
    # Run k6 with key_count environment variable
    $process = Start-Process -FilePath "k6" `
        -ArgumentList "run", `
                      "--out", "json=$($OUTPUT_FILE)_temp", `
                      "-e", "KEY_COUNT=$key_count", `
                      $k6_file `
        -NoNewWindow -PassThru -Wait
    
    if ($process.ExitCode -eq 0) {
        Write-Host "    ✓ Test completed" -ForegroundColor Green
        
        # Append results to main output
        if (Test-Path "$($OUTPUT_FILE)_temp") {
            Get-Content "$($OUTPUT_FILE)_temp" | Add-Content $OUTPUT_FILE
            Remove-Item "$($OUTPUT_FILE)_temp"
        }
    } else {
        Write-Host "    ✗ Test failed (exit code: $($process.ExitCode))" -ForegroundColor Red
    }
    
    # Brief pause between tests
    Start-Sleep -Seconds 2
}

# ============================================================================
# STEP 4: Verify Results
# ============================================================================
Write-Host "`n=== STEP 4: Verifying Results ===" -ForegroundColor Cyan

if (Test-Path $OUTPUT_FILE) {
    $file_size = (Get-Item $OUTPUT_FILE).Length
    $line_count = (Get-Content $OUTPUT_FILE | Measure-Object -Line).Lines
    
    Write-Host "✓ Results file: $OUTPUT_FILE" -ForegroundColor Green
    Write-Host "  File size: $([math]::Round($file_size / 1MB, 2)) MB" -ForegroundColor Green
    Write-Host "  Lines (metrics): $line_count" -ForegroundColor Green
} else {
    Write-Host "✗ Results file not created" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 5: Analyze Results
# ============================================================================
Write-Host "`n=== STEP 5: Analyzing Results ===" -ForegroundColor Cyan
Write-Host "Command: python analyze_scale_results.py $OUTPUT_FILE scale_analysis.md" -ForegroundColor Yellow

python analyze_scale_results.py $OUTPUT_FILE "scale_analysis.md"

if (Test-Path "scale_analysis.md") {
    Write-Host "`n✓ Markdown table generated: scale_analysis.md" -ForegroundColor Green
    Write-Host "`n--- Output Preview ---" -ForegroundColor Cyan
    Get-Content "scale_analysis.md" | Select-Object -First 30
}

Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "RQ2b COMPLETE - Scale test analysis saved!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green
