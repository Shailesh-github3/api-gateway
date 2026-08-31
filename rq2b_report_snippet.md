## RQ2b: Scale Testing - Latency Impact

### Latency Percentiles by Scale

| Scale (Keys) | p50 (ms) | p95 (ms) | p99 (ms) | Mean (ms) | Std Dev |
|---|---|---|---|---|---|
|         100 |    47.60 |    78.15 |    84.74 |     51.27 |   18.07 |
|      10,000 |    56.20 |    93.81 |   103.49 |     54.56 |   23.89 |
|   1,000,000 |    78.25 |   132.30 |   153.52 |     75.53 |   35.16 |

### Key Findings

- **Baseline (100 keys)**: p50=47.60ms, p95=78.15ms, p99=84.74ms
- **Peak Load (1M keys)**: p50=78.25ms, p95=132.30ms, p99=153.52ms
- **Latency increase**: p50 +64.4%, p95 +69.3%, p99 +81.2%

### Interpretation

As the API key store grows from 100 to 1 million keys, latency increases moderately:
- p50 latency increases by 64.4% (from 47.6ms to 78.2ms)
- p95 latency increases by 69.3% (from 78.1ms to 132.3ms)
- p99 latency increases by 81.2% (from 84.7ms to 153.5ms)

This demonstrates that the gateway maintains reasonable latency even with 1M API keys in the store,
confirming scalability for production deployments handling large key inventories.
