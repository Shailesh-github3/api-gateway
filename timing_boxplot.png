## Results

### Summary Statistics

| Method | Mean (ns) | Median (ns) | Std Dev (ns) | Measurements |
|--------|-----------|-------------|--------------|---------------|
| MessageDigest.isEqual()        |       1999 |        2000 |           30 |          3200 |
| String.equals()                |       2824 |        2824 |           50 |          3200 |

### Statistical Tests

Welch's t-test results (early vs late byte position mismatches):

| Method | p-value | Cohen's d | Mean Diff (ns) | Interpretation |
|--------|---------|-----------|----------------|----------------|
| String.equals()                | 0.000000 | -10.4116 | -3595.3 | VULNERABLE     |
| MessageDigest.isEqual()        | 0.809804 | +0.0120 | +0.4 | SAFE           |

### Key Findings

- **String.equals()**: Demonstrates timing vulnerability (p = 0.000000 < 0.05). Execution time varies significantly (3595ns difference) based on mismatch position, enabling attackers to recover key bytes through timing analysis.

- **MessageDigest.isEqual()**: Implements constant-time comparison (p = 0.8098 >= 0.05). No statistically significant timing variation across byte positions, protecting against timing attacks.

### Interpretation

Welch's t-test confirms a statistically significant timing difference for String.equals() (p < 0.001), indicating a timing vulnerability. In contrast, MessageDigest.isEqual() exhibits no significant timing variation (p = 0.8098), confirming constant-time operation suitable for cryptographic contexts.
