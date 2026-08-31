#!/usr/bin/env python3
"""
RQ2b: Scale test analysis.
Processes k6 NDJSON output from scale tests at different DB sizes (100, 10K, 1M keys).
Computes p50, p95, p99 latency per (key_count, method) pair.
Outputs a clean Markdown table comparing O(1) prefix lookup vs O(N) naive scan.
"""

import json
import sys
from collections import defaultdict
import numpy as np
import pandas as pd

def parse_k6_scale_results(file_path):
    """
    Parse k6 NDJSON output from scale tests.
    Each line contains metric data with tags indicating key_count and method.
    Returns dict: {(key_count, method): [latencies_ms]}
    """
    scale_results = defaultdict(list)
    
    try:
        with open(file_path, 'r') as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    obj = json.loads(line)
                    metric = obj.get('metric', '')
                    
                    if metric == 'http_reqs' or metric == 'http_req_duration':
                        data = obj.get('data', {})
                        tags = data.get('tags', {})
                        
                        # Extract key_count and method from tags
                        key_count = tags.get('key_count', 'unknown')
                        method = tags.get('method', 'unknown')
                        
                        # Duration is typically in milliseconds
                        duration_ms = data.get('time', 0)
                        
                        scale_results[(key_count, method)].append(duration_ms)
                except json.JSONDecodeError:
                    pass
    
    except FileNotFoundError:
        print(f"Error: {file_path} not found")
        return None
    
    return scale_results

def compute_percentiles(latencies, percentiles=[50, 95, 99]):
    """Compute percentiles for latency data."""
    if not latencies:
        return {}
    return {f'p{p}': np.percentile(latencies, p) for p in percentiles}

def generate_markdown_table(scale_results):
    """
    Generate a Markdown table comparing latency metrics by key count and method.
    """
    if not scale_results:
        return "No data available"
    
    # Organize data
    data = []
    key_counts = sorted(set(kc for kc, _ in scale_results.keys()), 
                       key=lambda x: int(x) if x != 'unknown' else -1)
    methods = sorted(set(m for _, m in scale_results.keys()))
    
    for key_count in key_counts:
        for method in methods:
            key = (key_count, method)
            if key in scale_results:
                latencies = scale_results[key]
                percentiles = compute_percentiles(latencies, [50, 95, 99])
                
                data.append({
                    'Key Count': key_count,
                    'Method': method,
                    'Count': len(latencies),
                    'Min (ms)': f"{min(latencies):.2f}",
                    'p50 (ms)': f"{percentiles['p50']:.2f}",
                    'p95 (ms)': f"{percentiles['p95']:.2f}",
                    'p99 (ms)': f"{percentiles['p99']:.2f}",
                    'Max (ms)': f"{max(latencies):.2f}",
                })
    
    # Create DataFrame for prettier output
    df = pd.DataFrame(data)
    
    # Generate Markdown
    markdown = "## Scale Test Results: Latency Analysis\n\n"
    markdown += "Comparison of O(1) prefix lookup vs O(N) naive scan at different database sizes.\n\n"
    markdown += df.to_markdown(index=False)
    markdown += "\n\n"
    
    # Add interpretation
    markdown += "### Interpretation\n\n"
    markdown += "- **p50 (median)**: 50% of requests complete by this time\n"
    markdown += "- **p95**: 95% of requests complete by this time (tail latency)\n"
    markdown += "- **p99**: 99% of requests complete by this time (extreme tail)\n\n"
    
    # Analyze scaling behavior
    markdown += "### Scaling Analysis\n\n"
    for method in methods:
        markdown += f"#### {method}\n"
        method_results = [(kc, scale_results.get((kc, method), [])) 
                         for kc in key_counts]
        
        for i, (kc, lats) in enumerate(method_results):
            if lats:
                p50 = np.percentile(lats, 50)
                if i > 0:
                    prev_p50 = np.percentile(method_results[i-1][1], 50) if method_results[i-1][1] else p50
                    ratio = p50 / prev_p50 if prev_p50 > 0 else 1
                    markdown += f"- **{kc} keys**: p50 = {p50:.2f}ms (ratio from previous: {ratio:.2f}x)\n"
                else:
                    markdown += f"- **{kc} keys**: p50 = {p50:.2f}ms\n"
        markdown += "\n"
    
    return markdown

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_scale_results.py <ndjson_file> [output_markdown]")
        print("  ndjson_file:     Path to k6 NDJSON output (e.g., rq2b_scale_results.json)")
        print("  output_markdown: Output Markdown file (default: scale_analysis.md)")
        sys.exit(1)
    
    ndjson_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else 'scale_analysis.md'
    
    print(f"Parsing scale test results from: {ndjson_file}")
    scale_results = parse_k6_scale_results(ndjson_file)
    
    if scale_results is None:
        sys.exit(1)
    
    print(f"Found {len(scale_results)} (key_count, method) combinations")
    for (kc, method), lats in sorted(scale_results.items()):
        print(f"  {kc} keys, {method}: {len(lats)} requests")
    
    # Generate Markdown table
    markdown_output = generate_markdown_table(scale_results)
    
    # Save to file
    try:
        with open(output_file, 'w') as f:
            f.write(markdown_output)
        print(f"\n✓ Markdown table saved to: {output_file}")
        print("\n" + markdown_output)
    except IOError as e:
        print(f"Error writing to {output_file}: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
