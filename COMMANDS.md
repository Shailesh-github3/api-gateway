# Quick PowerShell Commands Reference

## Prerequisites
```powershell
# Verify k6 is installed
k6 version

# Verify Python packages
python -c "import pandas, matplotlib, seaborn, scipy; print('OK')"

# If missing: pip install pandas matplotlib seaborn scipy
```

## API Key Setup (Required First)
```powershell
$headers = @{
    "Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("admin:admin"))
    "Content-Type" = "application/json"
}

$body = @{
    ownerId = "owner-123"
    tier = "STARTER"
    scopes = @("read")
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/admin/api-keys" `
    -Method POST -Headers $headers -Body $body

Write-Host "API Key: $($response.raw_key)"
Write-Host "⚠ SAVE THIS KEY NOW - never retrievable again!"
```

---

## RQ1: Fault Injection Test

### Option A: Automated (Easiest)
```powershell
powershell -File scripts/rq1-fault-injection.ps1
```
This runs everything in one command.

### Option B: Manual Steps

**Terminal 1** - Run k6 test:
```powershell
$key = "sk_live_..."  # Your API key

# Update script
(Get-Content load-tests/rq1-fault-injection.js) -replace "__REPLACE_WITH_ACTUAL_KEY__", $key | `
    Set-Content load-tests/rq1-fault-injection.js

# Run test (runs for 30 seconds)
k6 run --out json=rq1_results.json load-tests/rq1-fault-injection.js
```

**Terminal 2** - Inject latency (while k6 is running):
```powershell
# Wait ~5 seconds for baseline
Start-Sleep -Seconds 5

# Inject 5000ms latency
$body = @{
    name = "slow_redis"
    type = "latency"
    stream = "upstream"
    toxicity = 1.0
    attributes = @{ latency = 5000 }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8474/proxies/redis_proxy/toxics" `
    -Method POST -ContentType "application/json" -Body $body

Write-Host "✓ Latency injected - wait 10 seconds..."
Start-Sleep -Seconds 10

# Remove latency (recovery)
Invoke-RestMethod -Uri "http://localhost:8474/proxies/redis_proxy/toxics/slow_redis" -Method DELETE
Write-Host "✓ Latency removed - recovery in progress"
```

### Analyze RQ1 Results:
```powershell
python analyze_k6_results.py rq1_results.json rq1_analysis.png 5 15

# Output: rq1_analysis.png (2-subplot plot showing success rate and volume)
```

---

## RQ2a: Timing-Attack Analysis

### Option A: Automated
```powershell
powershell -File scripts/rq2a-timing-analysis.ps1
```

### Option B: Manual Steps
```powershell
# 1. Compile
javac -d target/test-classes src/test/java/com/gateway/timing/TimingAttackHarness.java

# 2. Run (generates timing_results.csv)
cd target/test-classes
java com.gateway.timing.TimingAttackHarness 100 "../../timing_results.csv"
cd ../..

# 3. Analyze
python analyze_timing.py timing_results.csv

# Output: timing_analysis.png + Welch's t-test results in console
```

---

## RQ2b: Scale Test (Verification Latency)

### Option A: Automated
```powershell
powershell -File scripts/rq2b-scale-test.ps1
```

### Option B: Manual Steps
```powershell
$key = "sk_live_..."  # Your API key

# Update script
(Get-Content load-tests/rq2b-scale-test.js) -replace "__REPLACE_WITH_ACTUAL_KEY__", $key | `
    Set-Content load-tests/rq2b-scale-test.js

# Run 3 tests (each 30 seconds) - choose one or all:
k6 run --out json=rq2b_100_results.json -e KEY_COUNT=100 load-tests/rq2b-scale-test.js
k6 run --out json=rq2b_10k_results.json -e KEY_COUNT=10000 load-tests/rq2b-scale-test.js
k6 run --out json=rq2b_1m_results.json -e KEY_COUNT=1000000 load-tests/rq2b-scale-test.js

# Combine results
Get-Content rq2b_100_results.json | Add-Content rq2b_scale_results.json
Get-Content rq2b_10k_results.json | Add-Content rq2b_scale_results.json
Get-Content rq2b_1m_results.json | Add-Content rq2b_scale_results.json

# Analyze
python analyze_scale_results.py rq2b_scale_results.json scale_analysis.md

# Output: scale_analysis.md (Markdown table with p50/p95/p99)
```

---

## Verify Output Files

### RQ1
```powershell
# Check file exists and size
ls -l rq1_results.json
wc -l rq1_results.json  # Line count (metrics)

# Show first 3 lines
Get-Content rq1_results.json -TotalCount 3
```

### RQ2a
```powershell
ls -l timing_results.csv
head -5 timing_results.csv  # First 5 rows with header
wc -l timing_results.csv    # Total rows
```

### RQ2b
```powershell
ls -l scale_analysis.md
Get-Content scale_analysis.md  # Show entire analysis
```

---

## Debug Commands

### Check API is Running
```powershell
Invoke-RestMethod http://localhost:8080/v1/example-resource `
    -Headers @{"X-API-Key"="sk_live_..."} -ErrorAction Stop
```

### Check Toxiproxy Status
```powershell
# List proxies
Invoke-RestMethod http://localhost:8474/proxies

# List toxics on redis_proxy
Invoke-RestMethod http://localhost:8474/proxies/redis_proxy/toxics
```

### Verify Python Dependencies
```powershell
python -c "import pandas, matplotlib, seaborn, scipy; print('All OK')"

# Install if missing
pip install --upgrade pandas matplotlib seaborn scipy
```

### Clean Up Results
```powershell
# Remove all generated test files
Remove-Item rq1_results.json, rq1_analysis.png, timing_results.csv, timing_analysis.png, `
           rq2b_scale_results.json, scale_analysis.md -ErrorAction SilentlyContinue
```

---

## Example: Complete Workflow (All Tests)

```powershell
# 1. Get API key
$key = "sk_live_..."  # From admin endpoint

# 2. RQ1 - Run in background while you inject faults
Start-Process powershell -ArgumentList "k6 run --out json=rq1_results.json -e KEY=$key load-tests/rq1-fault-injection.js"

# 3. After ~5s, inject latency (in new terminal)
Start-Sleep -Seconds 5
$body = @{
    name = "slow_redis"
    type = "latency"
    stream = "upstream"
    toxicity = 1.0
    attributes = @{ latency = 5000 }
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8474/proxies/redis_proxy/toxics" -Method POST -ContentType "application/json" -Body $body
Start-Sleep -Seconds 10
Invoke-RestMethod -Uri "http://localhost:8474/proxies/redis_proxy/toxics/slow_redis" -Method DELETE

# 4. When k6 completes, analyze RQ1
python analyze_k6_results.py rq1_results.json rq1_analysis.png 5 15

# 5. RQ2a - Timing analysis
javac -d target/test-classes src/test/java/com/gateway/timing/TimingAttackHarness.java
cd target/test-classes; java com.gateway.timing.TimingAttackHarness 100 "../../timing_results.csv"; cd ../..
python analyze_timing.py timing_results.csv

# 6. RQ2b - Scale tests
k6 run --out json=rq2b_100_results.json -e KEY_COUNT=100 load-tests/rq2b-scale-test.js
k6 run --out json=rq2b_10k_results.json -e KEY_COUNT=10000 load-tests/rq2b-scale-test.js
k6 run --out json=rq2b_1m_results.json -e KEY_COUNT=1000000 load-tests/rq2b-scale-test.js
Get-Content rq2b_100_results.json, rq2b_10k_results.json, rq2b_1m_results.json | Add-Content rq2b_scale_results.json
python analyze_scale_results.py rq2b_scale_results.json scale_analysis.md

# 7. Review outputs
echo "✓ RQ1: rq1_analysis.png"
echo "✓ RQ2a: timing_analysis.png"
echo "✓ RQ2b: scale_analysis.md"
```

---

## Common Issues & Fixes

| Issue | Fix |
|-------|-----|
| "k6: command not found" | Install k6 from https://k6.io/docs/getting-started/installation/ |
| "Connection refused" | Check app is running: `Invoke-RestMethod http://localhost:8080/health` |
| "401 Unauthorized" | Regenerate API key using admin endpoint |
| "No module named pandas" | `pip install pandas matplotlib seaborn scipy` |
| "TimeoutError" in k6 | Expected for RQ1 - timeout is 2s but injected latency is 5s |
| "Cannot inject toxic" | Verify Toxiproxy running: `Invoke-RestMethod http://localhost:8474/proxies` |

---

## File Locations
```
Root: d:\SpringBoot\apigateway\api-gateway\

k6 Scripts:
  load-tests/rq1-fault-injection.js
  load-tests/rq2b-scale-test.js

Python Analyzers:
  analyze_k6_results.py
  analyze_timing.py
  analyze_scale_results.py

Java Timing Harness:
  src/test/java/com/gateway/timing/TimingAttackHarness.java

PowerShell Scripts:
  scripts/rq1-fault-injection.ps1
  scripts/rq2a-timing-analysis.ps1
  scripts/rq2b-scale-test.ps1

Documentation:
  TESTING_GUIDE.md (this file)
  COMMANDS.md (this reference)

Output Files (Generated):
  rq1_results.json + rq1_analysis.png
  timing_results.csv + timing_analysis.png
  rq2b_scale_results.json + scale_analysis.md
```
