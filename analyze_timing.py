#!/usr/bin/env python3
"""
RQ2a: Timing-attack validation analysis.
Loads timing_results.csv with columns: method,byte_position,execution_time_ns
Produces box plots and runs Welch's t-test comparing early vs late mismatch positions.
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from scipy.stats import ttest_ind
import numpy as np
import sys

def load_timing_data(csv_path):
    """Load timing results from CSV."""
    df = pd.read_csv(csv_path)
    print(f"Loaded {len(df)} timing measurements from {csv_path}")
    print(f"Columns: {df.columns.tolist()}")
    print(f"Methods: {df['method'].unique().tolist()}")
    print(f"Byte positions: {sorted(df['byte_position'].unique())}\n")
    return df

def plot_execution_times(df, output_path='timing_analysis.png'):
    """
    Create box plots of execution time by byte_position, one subplot per method.
    """
    methods = df['method'].unique()
    fig, axes = plt.subplots(1, len(methods), figsize=(14, 5))
    
    if len(methods) == 1:
        axes = [axes]
    
    for idx, method in enumerate(methods):
        method_data = df[df['method'] == method]
        
        sns.boxplot(
            data=method_data,
            x='byte_position',
            y='execution_time_ns',
            ax=axes[idx]
        )
        axes[idx].set_title(f'{method}')
        axes[idx].set_xlabel('Byte Position')
        axes[idx].set_ylabel('Execution Time (ns)')
        axes[idx].grid(axis='y', alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Box plot saved to: {output_path}")
    plt.close()

def run_statistical_tests(df):
    """
    Run Welch's t-test comparing early vs late mismatch positions for each method.
    Early = positions 0-25% quantile
    Late = positions 75-100% quantile
    """
    print("\n" + "="*70)
    print("STATISTICAL ANALYSIS: Early vs Late Byte Position Mismatch")
    print("="*70 + "\n")
    
    methods = df['method'].unique()
    
    for method in methods:
        method_data = df[df['method'] == method]
        positions = sorted(method_data['byte_position'].unique())
        
        # Define early and late based on quantiles
        q25 = np.percentile(positions, 25)
        q75 = np.percentile(positions, 75)
        
        early_data = method_data[method_data['byte_position'] <= q25]['execution_time_ns']
        late_data = method_data[method_data['byte_position'] >= q75]['execution_time_ns']
        
        if len(early_data) > 0 and len(late_data) > 0:
            t_stat, p_value = ttest_ind(early_data, late_data, equal_var=False)
            
            print(f"Method: {method}")
            print(f"  Early mismatch (byte_position <= {q25:.1f}): n={len(early_data)}, mean={early_data.mean():.1f}ns, std={early_data.std():.1f}ns")
            print(f"  Late mismatch (byte_position >= {q75:.1f}): n={len(late_data)}, mean={late_data.mean():.1f}ns, std={late_data.std():.1f}ns")
            print(f"  Welch's t-test: t-statistic={t_stat:.4f}, p-value={p_value:.6f}")
            
            if p_value < 0.05:
                print(f"  *** SIGNIFICANT DIFFERENCE (p < 0.05) ***")
            else:
                print(f"  No significant difference (p >= 0.05)")
            print()

def main():
    csv_path = 'timing_results.csv'
    
    if len(sys.argv) > 1:
        csv_path = sys.argv[1]
    
    try:
        df = load_timing_data(csv_path)
        plot_execution_times(df, 'timing_analysis.png')
        run_statistical_tests(df)
        print("\n✓ Analysis complete!")
        
    except FileNotFoundError:
        print(f"Error: {csv_path} not found.")
        print("Expected columns: method, byte_position, execution_time_ns")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
