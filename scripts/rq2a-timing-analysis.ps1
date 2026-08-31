# PowerShell Scripts for RQ2a: Timing-Attack Validation

# ============================================================================
# RQ2a: Timing-Attack Validation (Constant-Time vs Naive Comparison)
# ============================================================================

# Prerequisites:
# - Java compiler (javac) available in PATH
# - Python 3 with pandas, matplotlib, seaborn, scipy installed
# - Timing harness source at: src/test/java/com/gateway/timing/TimingAttackHarness.java

# ============================================================================
# STEP 1: Compile Timing Harness
# ============================================================================
Write-Host "=== RQ2a: Compiling Timing Harness ===" -ForegroundColor Cyan

$harness_source = "src/test/java/com/gateway/timing/TimingAttackHarness.java"
$output_dir = "target/test-classes"

if (-not (Test-Path $harness_source)) {
    Write-Host "✗ Harness source not found: $harness_source" -ForegroundColor Red
    exit 1
}

Write-Host "Compiling $harness_source..." -ForegroundColor Yellow
javac -d $output_dir $harness_source

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Compilation successful" -ForegroundColor Green
} else {
    Write-Host "✗ Compilation failed" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 2: Run Timing Harness (100 iterations per position)
# ============================================================================
Write-Host "`n=== STEP 2: Running Timing Harness ===" -ForegroundColor Cyan
Write-Host "This will test 32 byte positions with 100 iterations each (3,200 measurements)" -ForegroundColor Gray
Write-Host "Estimated time: 30-60 seconds..." -ForegroundColor Gray

$output_csv = "timing_results.csv"

# Navigate to output dir and run
Push-Location $output_dir
java com.gateway.timing.TimingAttackHarness 100 "../../$output_csv"
Pop-Location

if (Test-Path $output_csv) {
    Write-Host "✓ Timing results written to: $output_csv" -ForegroundColor Green
    
    # Show CSV stats
    $csv_lines = (Get-Content $output_csv | Measure-Object -Line).Lines
    Write-Host "  Total rows: $csv_lines" -ForegroundColor Green
} else {
    Write-Host "✗ Output file not found: $output_csv" -ForegroundColor Red
    exit 1
}

# ============================================================================
# STEP 3: Verify Python Dependencies
# ============================================================================
Write-Host "`n=== STEP 3: Verifying Python Dependencies ===" -ForegroundColor Cyan

$python_packages = @("pandas", "matplotlib", "seaborn", "scipy")
$all_installed = $true

foreach ($package in $python_packages) {
    try {
        $output = python -c "import $package; print('OK')" 2>&1
        Write-Host "✓ $package installed" -ForegroundColor Green
    } catch {
        Write-Host "✗ $package not found" -ForegroundColor Red
        $all_installed = $false
    }
}

if (-not $all_installed) {
    Write-Host "`nInstalling missing packages..." -ForegroundColor Yellow
    pip install pandas matplotlib seaborn scipy
}

# ============================================================================
# STEP 4: Run Statistical Analysis
# ============================================================================
Write-Host "`n=== STEP 4: Running Statistical Analysis ===" -ForegroundColor Cyan
Write-Host "Command: python analyze_timing.py $output_csv" -ForegroundColor Yellow

python analyze_timing.py $output_csv

# ============================================================================
# STEP 5: Review Results
# ============================================================================
Write-Host "`n=== STEP 5: Results Summary ===" -ForegroundColor Green

if (Test-Path "timing_analysis.png") {
    Write-Host "✓ Box plot saved: timing_analysis.png" -ForegroundColor Green
    Write-Host "  Location: $(Resolve-Path 'timing_analysis.png')" -ForegroundColor Gray
    Write-Host "`n  This plot shows:" -ForegroundColor Cyan
    Write-Host "  - String.equals(): vulnerable to timing attacks (varies by byte position)" -ForegroundColor Yellow
    Write-Host "  - MessageDigest.isEqual(): constant-time (flat distribution)" -ForegroundColor Green
}

Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "RQ2a COMPLETE - Timing analysis saved!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green
