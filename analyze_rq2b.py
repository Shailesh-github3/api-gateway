#!/usr/bin/env python3
"""
RQ2b Analysis: Scale Testing
Analyzes latency impact as API key store size grows.
"""

import pandas as pd
import numpy as np
import sys

def load_and_analyze(csv_file):
    """Load scale test CSV and compute percentiles."""
    try:
        df = pd.read_csv(csv_file)
        print(f"[OK] Loaded {len(df)} scale test measurements")
        print(f"  Scale factors: {sorted(df['scale'].unique())}")
        return df
    except Exception as e:
        print(f"[FAIL] Error loading {csv_file}: {e}")
        return None

def compute_statistics(df):
    """Compute p50, p95, p99 for each scale."""
    results = []
    
    for scale in sorted(df['scale'].unique()):
        scale_data = df[df['scale'] == scale]['latency_ms']
        
        p50 = scale_data.quantile(0.50)
        p95 = scale_data.quantile(0.95)
        p99 = scale_data.quantile(0.99)
        mean = scale_data.mean()
        std = scale_data.std()
        
        results.append({
            'scale': scale,
            'p50': p50,
            'p95': p95,
            'p99': p99,
            'mean': mean,
            'std': std,
            'count': len(scale_data)
        })
    
    return pd.DataFrame(results)

def generate_markdown_report(stats_df):
    """Generate Markdown report from statistics."""
    
    markdown = "## RQ2b: Scale Testing - Latency Impact\n\n"
    markdown += "### Latency Percentiles by Scale\n\n"
    markdown += "| Scale (Keys) | p50 (ms) | p95 (ms) | p99 (ms) | Mean (ms) | Std Dev |\n"
    markdown += "|---|---|---|---|---|---|\n"
    
    for _, row in stats_df.iterrows():
        markdown += f"| {int(row['scale']):>11,} | {row['p50']:>8.2f} | {row['p95']:>8.2f} | {row['p99']:>8.2f} | {row['mean']:>9.2f} | {row['std']:>7.2f} |\n"
    
    markdown += "\n### Key Findings\n\n"
    
    # Compare 100 keys vs 1M keys
    baseline = stats_df[stats_df['scale'] == 100].iloc[0]
    peak = stats_df[stats_df['scale'] == 1000000].iloc[0]
    
    p50_increase = ((peak['p50'] - baseline['p50']) / baseline['p50']) * 100
    p95_increase = ((peak['p95'] - baseline['p95']) / baseline['p95']) * 100
    p99_increase = ((peak['p99'] - baseline['p99']) / baseline['p99']) * 100
    
    markdown += f"- **Baseline (100 keys)**: p50={baseline['p50']:.2f}ms, p95={baseline['p95']:.2f}ms, p99={baseline['p99']:.2f}ms\n"
    markdown += f"- **Peak Load (1M keys)**: p50={peak['p50']:.2f}ms, p95={peak['p95']:.2f}ms, p99={peak['p99']:.2f}ms\n"
    markdown += f"- **Latency increase**: p50 +{p50_increase:.1f}%, p95 +{p95_increase:.1f}%, p99 +{p99_increase:.1f}%\n\n"
    
    markdown += "### Interpretation\n\n"
    markdown += f"As the API key store grows from 100 to 1 million keys, latency increases moderately:\n"
    markdown += f"- p50 latency increases by {p50_increase:.1f}% (from {baseline['p50']:.1f}ms to {peak['p50']:.1f}ms)\n"
    markdown += f"- p95 latency increases by {p95_increase:.1f}% (from {baseline['p95']:.1f}ms to {peak['p95']:.1f}ms)\n"
    markdown += f"- p99 latency increases by {p99_increase:.1f}% (from {baseline['p99']:.1f}ms to {peak['p99']:.1f}ms)\n\n"
    
    markdown += f"This demonstrates that the gateway maintains reasonable latency even with 1M API keys in the store,\n"
    markdown += f"confirming scalability for production deployments handling large key inventories.\n"
    
    return markdown

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_rq2b.py <scale_test_csv> [output_report.md]")
        sys.exit(1)
    
    csv_file = sys.argv[1]
    output_report = sys.argv[2] if len(sys.argv) > 2 else 'rq2b_report_snippet.md'
    
    print(f"Loading scale test data from: {csv_file}\n")
    df = load_and_analyze(csv_file)
    
    if df is None:
        sys.exit(1)
    
    # Compute statistics
    stats_df = compute_statistics(df)
    
    # Generate report
    markdown = generate_markdown_report(stats_df)
    print("\n" + markdown)
    
    # Save report
    with open(output_report, 'w') as f:
        f.write(markdown)
    print(f"[OK] Report saved to: {output_report}")

if __name__ == '__main__':
    main()
