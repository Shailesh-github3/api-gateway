# PowerShell Script for RQ2a: Timing-Attack Validation
#
# RQ2a timing analysis now runs as a JUnit test (TimingAttackTest) instead of a
# standalone Java harness. The test asserts that MessageDigest.isEqual (used by
# HashUtil.verify) is constant-time regardless of mismatch position.

Write-Host "=== RQ2a: Running timing-attack JUnit test ===" -ForegroundColor Cyan
Write-Host "This verifies HashUtil.verify uses constant-time comparison (MessageDigest.isEqual)" -ForegroundColor Gray
Write-Host "Command: mvn -Dtest=TimingAttackTest test" -ForegroundColor Yellow

mvn -Dtest=TimingAttackTest test

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ TimingAttackTest passed (constant-time verification OK)" -ForegroundColor Green
    Write-Host "`n  Result:" -ForegroundColor Cyan
    Write-Host "  - String.equals(): early-exit -> leaks byte position (vulnerable)" -ForegroundColor Yellow
    Write-Host "  - MessageDigest.isEqual(): constant-time (secure)" -ForegroundColor Green
    Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host "RQ2a COMPLETE - timing resistance confirmed" -ForegroundColor Green
    Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor Green
} else {
    Write-Host "`n✗ TimingAttackTest FAILED" -ForegroundColor Red
    exit 1
}