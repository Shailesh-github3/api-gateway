# Fault Injection & Performance Research Guide

This document provides step-by-step instructions for running fault injection and performance tests on the Spring Boot Rate-Limiting API Gateway.

## Prerequisites

### Software Requirements
- **Java**: JDK 11+ (for timing harness compilation)
- **Maven**: 3.6+ (for building)
- **k6**: v0.50+ (load testing tool - https://k6.io/docs/getting-started/installation/)
- **Python**: 3.8+ with packages:
  ```powershell
  pip install pandas matplotlib seaborn scipy
  ```
- **Toxiproxy**: Running on localhost:8474 (fault injection proxy)

### Services Running
- **Spring Boot App**: http://localhost:8080
- **Redis**: localhost:6379 (via Toxiproxy at localhost:26379 as "redis_proxy")
- **Toxiproxy**: http://localhost:8474

### API Key Setup
Before running any tests, generate an API key:

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

Write-Host "✓ API Key: $($response.raw_key)"
Write-Host "⚠ SAVE THIS KEY NOW - it cannot be retrieved again!"
```

---

## RQ1: Traffic Control Under Redis Fault Injection

**Research Question**: How does the API gateway handle requests when Redis introduces latency?

**What It Tests**:
- Baseline performance (0-5s, healthy Redis)
- Degradation under fault (5-15s, 5000ms latency injected)
- Recovery behavior (15-30s, latency removed)

### Quick Start

**Terminal 1** (Run k6 test):
```powershell
# Update script with your API key first
$key = "sk_live_..."  # Your API key from admin endpoint
(Get-Content load-tests/rq1-fault-injection.js) -replace "__REPLACE_WITH_ACTUAL_KEY__", $key | `
    Set-Content load-tests/rq1-fault-injection.js

# Run the test
k6 run --out json=rq1_results.json load-tests/rq1-fault-injection.js
```

**Terminal 2** (Inject fault after ~5 seconds):
```powershell
# Wait for baseline to complete
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

Write-Host "✓ Latency injected for 10 seconds"
Start-Sleep -Seconds 10

# Remove latency (recovery)
Invoke-RestMethod -Uri "http://localhost:8474/proxies/redis_proxy/toxics/slow_redis" `
    -Method DELETE

Write-Host "✓ Latency removed - recovery in progress"
```

### Analysis

```powershell
# Generate analysis plot (marks fault window 5-15s)
python analyze_k6_results.py rq1_results.json rq1_analysis.png 5 15

# Output: rq1_analysis.png
#   - Shows success rate drop during fault window
#   - Shows recovery after latency removal
```

### Expected Output
- **rq1_results.json**: NDJSON file with all request metrics
- **rq1_analysis.png**: Two subplots showing success rate and request volume over time

### Files
- `load-tests/rq1-fault-injection.js` - k6 load test script
- `analyze_k6_results.py` - k6 result analyzer
- `scripts/rq1-fault-injection.ps1` - Automated PowerShell script (all steps)

---

## RQ2a: Timing-Attack Validation

**Research Question**: Are API key hashes compared using constant-time functions?

**What It Tests**:
- Compares `String.equals()` (vulnerable) vs `MessageDigest.isEqual()` (constant-time)
- Measures execution time across all 32 bytes of SHA-256 hash
- Tests for timing side-channels that could leak key information

### Quick Start

**Step 1**: Compile timing harness
```powershell
javac -d target/test-classes src/test/java/com/gateway/timing/TimingAttackHarness.java
```

**Step 2**: Run harness (generates CSV)
```powershell
cd target/test-classes
java com.gateway.timing.TimingAttackHarness 100 "../../timing_results.csv"
cd ../..
```

**Step 3**: Analyze results
```powershell
python analyze_timing.py timing_results.csv
```

### Analysis Output
- **timing_analysis.png**: Box plots comparing execution times
  - **String.equals()**: High variance (vulnerable)
  - **MessageDigest.isEqual()**: Low variance (constant-time)
- **Console**: Welch's t-test results (early vs late byte position mismatches)

### Expected Results
- **p-value < 0.05** for String.equals() = TIMING VULNERABILITY
- **p-value >= 0.05** for MessageDigest.isEqual() = SAFE (constant-time)

### Files
- `src/test/java/com/gateway/timing/TimingAttackHarness.java` - Timing measurement harness
- `analyze_timing.py` - Statistical analysis and plotting
- `scripts/rq2a-timing-analysis.ps1` - Automated PowerShell script

### CSV Format
```
method,byte_position,execution_time_ns
String.equals(),0,1234567
String.equals(),0,1245678
MessageDigest.isEqual(),0,2000000
...
```

---

## RQ2b: Verification Latency at Scale

**Research Question**: Does key verification latency scale linearly or sublinearly with DB size?

**What It Tests**:
- Verification at 100, 10K, and 1M stored keys
- Measures p50, p95, p99 latencies
- Compares O(1) prefix lookup vs O(N) naive scan

### Quick Start

```powershell
# For 100 keys
k6 run --out json=rq2b_100_results.json -e KEY_COUNT=100 load-tests/rq2b-scale-test.js

# For 10,000 keys
k6 run --out json=rq2b_10k_results.json -e KEY_COUNT=10000 load-tests/rq2b-scale-test.js

# For 1,000,000 keys
k6 run --out json=rq2b_1m_results.json -e KEY_COUNT=1000000 load-tests/rq2b-scale-test.js
```

### Combined Analysis

```powershell
# Merge all results
Get-Content rq2b_100_results.json | Add-Content rq2b_scale_results.json
Get-Content rq2b_10k_results.json | Add-Content rq2b_scale_results.json
Get-Content rq2b_1m_results.json | Add-Content rq2b_scale_results.json

# Generate Markdown comparison table
python analyze_scale_results.py rq2b_scale_results.json scale_analysis.md
```

### Analysis Output
- **scale_analysis.md**: Markdown table with:
  - p50, p95, p99 latencies for each (key_count, method) pair
  - Scaling analysis showing latency growth ratio
  - Interpretation and recommendations

### Example Output Table
```markdown
| Key Count | Method                | p50 (ms) | p95 (ms) | p99 (ms) |
|-----------|----------------------|----------|----------|----------|
| 100       | Prefix Lookup (O1)    | 1.23     | 2.45     | 3.67     |
| 100       | Naive Scan (On)      | 2.34     | 3.56     | 4.78     |
| 10000     | Prefix Lookup (O1)    | 1.25     | 2.48     | 3.70     |
| 10000     | Naive Scan (On)      | 12.45    | 18.56    | 24.78    |
| 1000000   | Prefix Lookup (O1)    | 1.27     | 2.50     | 3.72     |
| 1000000   | Naive Scan (On)      | 1245.67  | 1856.78  | 2478.90  |
```

### Files
- `load-tests/rq2b-scale-test.js` - k6 scale test script
- `analyze_scale_results.py` - Scale result analyzer (p-percentile computation)
- `scripts/rq2b-scale-test.ps1` - Automated PowerShell script

---

## General: k6 Result Analysis

### Analyzing Any k6 Test

```powershell
# Parse NDJSON, bucket by 1-second windows, plot
python analyze_k6_results.py <input.json> <output.png> [fault_start] [fault_end]

# Example with custom fault window
python analyze_k6_results.py my_test_results.json analysis.png 10 20
```

### Output
- **PNG/PDF plot** (300 DPI):
  - Top: Success rate % per second with fault window shaded
  - Bottom: Request volume per second
- **Console**: Summary statistics (total requests, success rate, min/max/avg)

### Interpreting the Plot
- **Green shaded region**: Normal operation
- **Red shaded region**: Fault window (injected latency)
- **Success rate drops to 0%**: Timeout behavior (requests exceed timeout threshold)
- **Recovery curve**: Shows how quickly system recovers

---

## Advanced: Full Automated Testing

### Option 1: Use PowerShell Scripts (Recommended for Windows)

```powershell
# RQ1 (fault injection)
powershell -File scripts/rq1-fault-injection.ps1

# RQ2a (timing analysis)
powershell -File scripts/rq2a-timing-analysis.ps1

# RQ2b (scale testing)
powershell -File scripts/rq2b-scale-test.ps1
```

### Option 2: Manual Step-by-Step

See individual sections above for manual commands.

---

## Troubleshooting

### k6 Error: "Connection refused"
**Cause**: App not running or wrong port  
**Fix**: Verify app is running on http://localhost:8080
```powershell
Invoke-RestMethod http://localhost:8080/v1/example-resource -Headers @{"X-API-Key"="your-key"}
```

### k6 Error: "All requests failed - timeout"
**Cause**: Request timeout (2000ms) too short for latency (5000ms)  
**Expected**: This is intentional for RQ1 - shows timeout behavior
**Note**: Timeout set to 2000ms < 5000ms injected latency

### Python Import Error: "No module named 'pandas'"
**Fix**: Install missing packages
```powershell
pip install pandas matplotlib seaborn scipy
```

### Toxiproxy Error: "Cannot POST /proxies/redis_proxy/toxics"
**Cause**: Toxiproxy not running or redis_proxy not configured  
**Fix**: Verify Toxiproxy is running and redis_proxy proxy exists
```powershell
Invoke-RestMethod http://localhost:8474/proxies
```

### "API key not found" or "401 Unauthorized"
**Cause**: Invalid or expired API key  
**Fix**: Generate new key using admin endpoint (see Prerequisites)

---

## Output Files Summary

| File | Format | Source | Purpose |
|------|--------|--------|---------|
| `rq1_results.json` | NDJSON | k6 | Raw fault injection metrics |
| `rq1_analysis.png` | PNG (300 DPI) | analyze_k6_results.py | Success rate plot with fault window |
| `timing_results.csv` | CSV | TimingAttackHarness.java | Raw timing measurements (3,200 rows) |
| `timing_analysis.png` | PNG (300 DPI) | analyze_timing.py | Box plots comparing methods |
| `rq2b_scale_results.json` | NDJSON | k6 | Raw scale test metrics |
| `scale_analysis.md` | Markdown | analyze_scale_results.py | Latency table with p50/p95/p99 |

---

## Research Publication Tips

### For Papers
1. **RQ1 Plot**: Include rq1_analysis.png showing fault injection/recovery
2. **RQ2a Plot**: Include timing_analysis.png (side-by-side box plots) + p-values
3. **RQ2b Table**: Include scale_analysis.md table in results section
4. **Discussion**: Compare findings to literature on API gateway resilience

### Statistical Significance
- RQ2a uses **Welch's t-test** (unequal variance) - p < 0.05 is significant
- Report both p-values and effect sizes (difference in means)

### Reproducibility
- All scripts are self-contained and generate timestamp-labeled outputs
- Store k6 NDJSON and CSV files in version control for reproducibility
- Document API key rotation (keys expire after 90 days)

---

## Support

For issues or questions:
1. Check Troubleshooting section
2. Verify all prerequisites are installed
3. Check app logs: `docker logs api-gateway`
4. Verify Toxiproxy: `curl -s http://localhost:8474/proxies | python -m json.tool`
