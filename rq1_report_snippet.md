## Results Summary

### Failure Behavior
- **HTTP statuses during fault**: 0, 200
- **Failure mode**: **fail-closed** (requests timeout/are rejected)

### Key Metrics
| Metric | Value |
|--------|-------|
| Baseline success rate | 92.5% |
| Fault window failure rate | 90.1% |
| Time to first failure (after injection) | 0.49s |
| Recovery success rate | 94.2% |

### Interpretation
The system exhibits **fail-closed behavior**: when Redis becomes unavailable, requests timeout (status 0) rather than returning stale or default values. This is the correct behavior for a payment gateway API, where **unavailability is preferable to inconsistency**.

### Is Fail-Closed Right for Payment Gateways?
Yes. For payment processing systems, fail-closed (reject transactions when state cannot be verified) is the correct choice over fail-open (allow transactions with stale data). Returning 5xx/0 timeout rather than accepting a payment under uncertainty prevents double-charging, lost funds, or fraud. The slight availability hit is an acceptable trade-off for correctness. Operators can mitigate downtime through Redis replication, monitoring, and graceful degradation (e.g., cached tiers) without compromising the core fail-closed principle.
