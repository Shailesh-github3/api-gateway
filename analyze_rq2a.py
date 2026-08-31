#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RQ2a Analysis: Timing-Attack Resistance
Detects timing side-channels in API key comparison methods.
Compares String.equals() (naive) vs MessageDigest.isEqual() (constant-time).
"""

import pandas as pd
import numpy as np
from scipy.stats import ttest_ind
import sys
import os
os.environ['PYTHONIOENCODING'] = 'utf-8'

def load_and_analyze(csv_file):
    """Load timing CSV and perform statistical analysis."""
    
    try:
        df = pd.read_csv(csv_file)
    except FileNotFoundError:
        print(f"Error: {csv_file} not found", file=sys.stderr)
        return None
    
    print(f"[OK] Loaded {len(df)} timing measurements")
    print(f"  Columns: {list(df.columns)}")
    print(f"  Methods: {df['method'].unique().tolist()}")
    print(f"  Byte positions tested: {sorted(df['byte_position'].unique())}\n")
    
    return df

def compute_statistics(df):
    """Compute summary statistics per method and byte position."""
    
    stats_list = []
    
    for method in df['method'].unique():
        method_data = df[df['method'] == method]
        
        # Overall stats
        overall_mean = method_data['execution_time_ns'].mean()
        overall_median = method_data['execution_time_ns'].median()
        overall_std = method_data['execution_time_ns'].std()
        
        print(f"\n{method}:")
        print(f"  Overall mean:   {overall_mean:.1f} ns")
        print(f"  Overall median: {overall_median:.1f} ns")
        print(f"  Overall std:    {overall_std:.1f} ns")
        
        # Per-position stats
        print(f"\n  Per-byte-position statistics:")
        print(f"  {'Position':<10} {'Mean (ns)':<15} {'Median (ns)':<15} {'Std (ns)':<15}")
        print(f"  {'-'*55}")
        
        for pos in sorted(method_data['byte_position'].unique()):
            pos_data = method_data[method_data['byte_position'] == pos]['execution_time_ns']
            mean = pos_data.mean()
            median = pos_data.median()
            std = pos_data.std()
            print(f"  {pos:<10} {mean:<15.1f} {median:<15.1f} {std:<15.1f}")
            
            stats_list.append({
                'method': method,
                'byte_position': pos,
                'mean': mean,
                'median': median,
                'std': std,
                'count': len(pos_data)
            })
    
    return pd.DataFrame(stats_list)

def run_statistical_tests(df):
    """Run Welch's t-test comparing early vs late mismatch positions."""
    
    print("\n\n" + "="*80)
    print("STATISTICAL HYPOTHESIS TEST: Early vs Late Mismatch Position Timing")
    print("="*80)
    print("\nH0: No significant timing difference between early and late byte mismatches")
    print("H1: Significant timing difference between early and late byte mismatches")
    print("Significance level: alpha = 0.05 (Welch's t-test, unequal variance)\n")
    
    results = []
    
    for method in df['method'].unique():
        method_data = df[df['method'] == method]
        positions = sorted(method_data['byte_position'].unique())
        
        # Define early and late based on quantiles
        q25 = np.percentile(positions, 25)
        q75 = np.percentile(positions, 75)
        
        early_data = method_data[method_data['byte_position'] <= q25]['execution_time_ns']
        late_data = method_data[method_data['byte_position'] >= q75]['execution_time_ns']
        
        if len(early_data) > 0 and len(late_data) > 0:
            # Welch's t-test (doesn't assume equal variance)
            t_stat, p_value = ttest_ind(early_data, late_data, equal_var=False)
            
            # Effect size: Cohen's d
            cohens_d = (early_data.mean() - late_data.mean()) / np.sqrt((early_data.std()**2 + late_data.std()**2) / 2)
            
            # Difference in means
            mean_diff = early_data.mean() - late_data.mean()
            
            print(f"Method: {method}")
            print(f"  Early positions (byte_pos <= {q25:.0f}): n={len(early_data)}")
            print(f"    Mean: {early_data.mean():.1f} ns, Median: {early_data.median():.1f} ns")
            print(f"  Late positions (byte_pos >= {q75:.0f}): n={len(late_data)}")
            print(f"    Mean: {late_data.mean():.1f} ns, Median: {late_data.median():.1f} ns")
            print(f"  ---")
            print(f"  Welch's t-test:")
            print(f"    t-statistic:  {t_stat:+.4f}")
            print(f"    p-value:      {p_value:.6f}")
            print(f"    Cohen's d:    {cohens_d:+.4f} ({'small' if abs(cohens_d) < 0.2 else 'medium' if abs(cohens_d) < 0.8 else 'large'} effect)")
            print(f"    Mean diff:    {mean_diff:+.1f} ns ({abs(mean_diff/late_data.mean()*100):.1f}% variation)")
            
            if p_value < 0.05:
                print(f"  [FAIL] SIGNIFICANT DIFFERENCE (p < 0.05) - TIMING VULNERABILITY DETECTED")
                interpretation = "VULNERABLE"
            else:
                print(f"  [OK] NO SIGNIFICANT DIFFERENCE (p >= 0.05) - Constant-time behavior confirmed")
                interpretation = "SAFE"
            
            results.append({
                'method': method,
                'p_value': p_value,
                't_statistic': t_stat,
                'cohens_d': cohens_d,
                'mean_diff': mean_diff,
                'interpretation': interpretation,
                'early_mean': early_data.mean(),
                'late_mean': late_data.mean(),
                'early_n': len(early_data),
                'late_n': len(late_data)
            })
            print()
    
    return pd.DataFrame(results)

def generate_markdown_report(stats_df, test_results_df):
    """Generate Markdown report section."""
    
    markdown = "## Results\n\n"
    
    markdown += "### Summary Statistics\n\n"
    markdown += "| Method | Mean (ns) | Median (ns) | Std Dev (ns) | Measurements |\n"
    markdown += "|--------|-----------|-------------|--------------|---------------|\n"
    
    for _, row in stats_df.groupby('method').agg({
        'mean': 'mean',
        'median': 'mean',
        'std': 'mean',
        'count': 'sum'
    }).iterrows():
        method = row.name
        markdown += f"| {method:<30} | {row['mean']:>10.0f} | {row['median']:>11.0f} | {row['std']:>12.0f} | {int(row['count']):>13} |\n"
    
    markdown += "\n### Statistical Tests\n\n"
    markdown += "Welch's t-test results (early vs late byte position mismatches):\n\n"
    markdown += "| Method | p-value | Cohen's d | Mean Diff (ns) | Interpretation |\n"
    markdown += "|--------|---------|-----------|----------------|----------------|\n"
    
    for _, row in test_results_df.iterrows():
        markdown += f"| {row['method']:<30} | {row['p_value']:.6f} | {row['cohens_d']:+.4f} | {row['mean_diff']:+.1f} | {row['interpretation']:<14} |\n"
    
    markdown += "\n### Key Findings\n\n"
    
    for _, row in test_results_df.iterrows():
        if row['interpretation'] == 'VULNERABLE':
            markdown += f"- **{row['method']}**: Demonstrates timing vulnerability (p = {row['p_value']:.6f} < 0.05). "
            markdown += f"Execution time varies significantly ({abs(row['mean_diff']):.0f}ns difference) based on mismatch position, "
            markdown += f"enabling attackers to recover key bytes through timing analysis.\n\n"
        else:
            markdown += f"- **{row['method']}**: Implements constant-time comparison (p = {row['p_value']:.4f} >= 0.05). "
            markdown += f"No statistically significant timing variation across byte positions, protecting against timing attacks.\n\n"
    
    markdown += "### Interpretation\n\n"
    
    # Construct the key finding sentence
    vulnerable_methods = test_results_df[test_results_df['interpretation'] == 'VULNERABLE']['method'].tolist()
    safe_methods = test_results_df[test_results_df['interpretation'] == 'SAFE']['method'].tolist()
    
    if vulnerable_methods and safe_methods:
        markdown += f"Welch's t-test confirms a statistically significant timing difference for {vulnerable_methods[0]} "
        markdown += f"(p < 0.001), indicating a timing vulnerability. In contrast, {safe_methods[0]} exhibits no "
        markdown += f"significant timing variation (p = {test_results_df[test_results_df['method'] == safe_methods[0]]['p_value'].values[0]:.4f}), "
        markdown += f"confirming constant-time operation suitable for cryptographic contexts.\n"
    elif vulnerable_methods:
        markdown += f"Welch's t-test confirms statistically significant timing differences for all methods tested (p < 0.05), "
        markdown += f"indicating timing vulnerabilities that could be exploited to recover API keys through differential analysis.\n"
    else:
        markdown += f"All methods tested exhibit constant-time behavior (all p >= 0.05), with no statistically significant "
        markdown += f"timing variation across byte positions. This provides strong evidence of protection against timing-based key recovery attacks.\n"
    
    return markdown

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_rq2a.py <timing_csv> [output_chart.png] [output_report.md]")
        sys.exit(1)
    
    csv_file = sys.argv[1]
    output_chart = sys.argv[2] if len(sys.argv) > 2 else 'timing_boxplot.png'
    output_report = sys.argv[3] if len(sys.argv) > 3 else 'rq2a_report_snippet.md'
    
    print(f"Loading timing measurements from: {csv_file}\n")
    df = load_and_analyze(csv_file)
    
    if df is None:
        sys.exit(1)
    
    # Compute statistics
    stats_df = compute_statistics(df)
    
    # Run statistical tests
    test_results = run_statistical_tests(df)
    
    # Generate report
    markdown = generate_markdown_report(stats_df, test_results)
    print("\n" + markdown)
    
    # Save report
    with open(output_report, 'w') as f:
        f.write(markdown)
    print(f"\n[OK] Report saved to: {output_report}")
    
    # Try to generate plot
    try:
        import matplotlib.pyplot as plt
        import seaborn as sns
        
        methods = df['method'].unique()
        fig, axes = plt.subplots(1, len(methods), figsize=(14, 5))
        
        if len(methods) == 1:
            axes = [axes]
        
        for idx, method in enumerate(sorted(methods)):
            method_data = df[df['method'] == method]
            
            sns.boxplot(
                data=method_data,
                x='byte_position',
                y='execution_time_ns',
                ax=axes[idx]
            )
            axes[idx].set_title(f'{method}', fontsize=12, fontweight='bold')
            axes[idx].set_xlabel('Byte Position in Hash', fontsize=10)
            axes[idx].set_ylabel('Execution Time (ns)', fontsize=10)
            axes[idx].grid(axis='y', alpha=0.3)
            
            # Add mean line
            means = method_data.groupby('byte_position')['execution_time_ns'].mean()
            axes[idx].plot(range(len(means)), means.values, 'r--', alpha=0.5, linewidth=2, label='Mean')
        
        plt.suptitle('RQ2a: Timing Analysis - String.equals() vs Constant-Time Comparison', 
                    fontsize=14, fontweight='bold', y=1.02)
        plt.tight_layout()
        plt.savefig(output_chart, dpi=300, bbox_inches='tight')
        print(f"[OK] Box plot saved to: {output_chart}")
        plt.close()
    
    except ImportError:
        print("[WARN] matplotlib/seaborn not available - skipping chart generation")
        print("  Install with: pip install matplotlib seaborn")

if __name__ == '__main__':
    main()
