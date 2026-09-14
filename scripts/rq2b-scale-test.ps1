# PowerShell Script for RQ2b: Scale / Volume Testing
#
# Measures request throughput and latency against /v1/example-resource with k6.
# The API key is passed via k6 env variables (API_KEY, BASE_URL); no file editing.

# Prerequisites:
# - k6 installed (https://k6.io/)
# - Python 3 with pandas
# - App running on http://localhost:8080
# - A valid API key (create via POST /admin/api-keys)

# ============================================================================
# CONFIGURATION
# ============================================================================

$BASE_URL = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }
$API_KEY = Read-Host -Prompt "Enter the API key (create one via POST /admin/api-keys if needed)"
$OUTPUT_FILE = "rq2b_scale_results.json"
$K6_FILE = "load-tests/rq2b-scale-test.js"

# Number of iterations to run (pass-through; the k6 script defaults duration to 30s)
$RUNS = if ($env:RUNS) { $env:RUNS } else { 250 }

if ([string]::IsNullOrWhiteSpace($API_KEY)) {
    Write-Host "✗ API key is required." -ForegroundColor Red
    exit 1
}

Write-Host "=== RQ2b: Scale Test ===" -ForegroundColor Cyan
Write-Host "Base URL: $BASE_URL" -ForegroundColor Gray
Write-Host "Output file: $OUTPUT_FILE" -ForegroundColor Gray

# ============================================================================
# STEP 1: Verify the app is reachable
# ============================================================================
Write-Host "`n=== STEP 1: Verifying app is reachable ===" -ForegroundColor Cyan
try {
    $null = Invoke-RestMethod -Uri "$BASE_URL/v1/example-resource" -Headers @{ "X-API-Key" = $API_KEY } -TimeoutSec 10
    Write-Host "✓ App is reachable (authenticated request succeeded)" -ForegroundColor Green
} catch {
    Write-Host "✗ App not reachable or key invalid." -ForegroundColor Red
    Write-Host "  Ensure the app is running and the key is valid." -ForegroundColor Yellow
    exit 1
}

# ============================================================================
# STEP 2: Run k6 scale test
# ============================================================================
Write-Host "`n=== STEP 2: Running k6 scale test ===" -ForegroundColor Cyan
Write-Host "Command: k6 run -e API_KEY=... -e BASE_URL=$BASE_URL -i $RUNS $K6_FILE" -ForegroundColor Yellow

if (-not (Test-Path $K6_FILE)) {
    Write-Host "✗ k6 script not found: $K6_FILE" -ForegroundColor Red
    exit 1
}

k6 run --out "json=$OUTPUT_FILE" -e "API_KEY=$API_KEY" -e "BASE_URL=$BASE_URL" -i $RUNS $K6_FILE

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ k6 run failed (exit code: $LASTEXITCODE)" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 3: Verify Results
# ============================================================================
Write-Host "`n=== STEP 3: Verifying Results ===" -ForegroundColor Cyan

if (Test-Path $OUTPUT_FILE) {
    $file_size = (Get-Item $OUTPUT_FILE).Length
    Write-Host "✓ Results file: $OUTPUT_FILE" -ForegroundColor Green
    Write-Host "  File size: $([math]::Round($file_size / 1MB, 2)) MB" -ForegroundColor Green
} else {
    Write-Host "✗ Results file not created" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 4: Analyze Results (optional)
# ============================================================================
Write-Host "`n=== STEP 4: Analyzing Results (optional) ===" -ForegroundColor Cyan

if (Test-Path "analyze_scale_results.py") {
    Write-Host "Command: python analyze_scale_results.py $OUTPUT_FILE scale_analysis.md" -ForegroundColor Yellow
    python analyze_scale_results.py $OUTPUT_FILE "scale_analysis.md"
    if (Test-Path "scale_analysis.md") {
        Write-Host "`n✓ Markdown table generated: scale_analysis.md" -ForegroundColor Green
    }
} else {
    Write-Host "Skipping analysis (analyze_scale_results.py not present). k6 summary is above." -ForegroundColor Gray
}

Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "RQ2b COMPLETE - scale test results saved!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green