#!/usr/bin/env python3
"""
k6 NDJSON Analysis Tool
Parses k6 output (NDJSON format), buckets requests into 1-second windows,
computes success/failure rates, and plots results with fault-injection window marked.
"""

import json
import sys
from collections import defaultdict
from datetime import datetime, timedelta
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

def parse_k6_ndjson(file_path):
    """
    Parse k6 NDJSON output file.
    Each line is a JSON object with 'metric' and 'data' fields.
    Returns list of (timestamp, status_code, duration) tuples.
    """
    requests = []
    
    try:
        with open(file_path, 'r') as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    obj = json.loads(line)
                    
                    # Extract metric type and timestamp
                    metric = obj.get('metric', '')
                    data = obj.get('data', {})
                    
                    # Look for HTTP request metrics
                    if metric == 'http_reqs':
                        tags = data.get('tags', {})
                        time_str = obj.get('time', '')
                        status = int(tags.get('status', 0))
                        
                        # Parse ISO 8601 timestamp
                        try:
                            if time_str:
                                # Remove 'Z' and parse as ISO format
                                ts = datetime.fromisoformat(time_str.replace('Z', '+00:00'))
                                requests.append({
                                    'timestamp': ts,
                                    'status': status,
                                    'duration': data.get('time', 0),
                                    'metric': metric
                                })
                        except:
                            pass
                except json.JSONDecodeError:
                    pass
    
    except FileNotFoundError:
        print(f"Error: {file_path} not found")
        return None
    
    return requests

def bucket_requests(requests, bucket_size_seconds=1):
    """
    Group requests into time buckets (1-second windows by default).
    Returns dict: {bucket_index: {'success': count, 'failed': count, 'total': count, 'success_rate': %}}
    """
    if not requests:
        return {}
    
    # Sort by timestamp
    requests = sorted(requests, key=lambda x: x['timestamp'])
    start_time = requests[0]['timestamp']
    
    buckets = defaultdict(lambda: {'success': 0, 'failed': 0})
    
    for req in requests:
        elapsed = (req['timestamp'] - start_time).total_seconds()
        bucket_idx = int(elapsed / bucket_size_seconds)
        
        if req['status'] == 200:
            buckets[bucket_idx]['success'] += 1
        else:
            buckets[bucket_idx]['failed'] += 1
    
    # Calculate success rate for each bucket
    result = {}
    for idx in sorted(buckets.keys()):
        total = buckets[idx]['success'] + buckets[idx]['failed']
        success_rate = (buckets[idx]['success'] / total * 100) if total > 0 else 0
        result[idx] = {
            'success': buckets[idx]['success'],
            'failed': buckets[idx]['failed'],
            'total': total,
            'success_rate': success_rate
        }
    
    return result

def plot_results(buckets, fault_window=(5, 15), output_file='k6_analysis.png'):
    """
    Plot success rate over time with fault-injection window marked.
    
    Args:
        buckets: dict from bucket_requests()
        fault_window: tuple (start_sec, end_sec) marking when fault was active
        output_file: path to save the plot
    """
    if not buckets:
        print("No data to plot")
        return
    
    indices = sorted(buckets.keys())
    times = np.array(indices)
    success_rates = np.array([buckets[idx]['success_rate'] for idx in indices])
    totals = np.array([buckets[idx]['total'] for idx in indices])
    
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))
    
    # Plot 1: Success rate over time
    ax1.plot(times, success_rates, marker='o', linewidth=2, markersize=6, label='Success Rate')
    ax1.axhline(y=100, color='green', linestyle='--', alpha=0.3, label='Perfect (100%)')
    ax1.axhline(y=0, color='red', linestyle='--', alpha=0.3, label='Complete Failure (0%)')
    
    # Mark fault injection window with shaded region
    if fault_window:
        ax1.axvspan(fault_window[0], fault_window[1], alpha=0.2, color='red', label='Fault Injection Window')
    
    ax1.set_xlabel('Time (seconds)')
    ax1.set_ylabel('Success Rate (%)')
    ax1.set_title('API Success Rate During Fault Injection')
    ax1.set_ylim(-5, 105)
    ax1.grid(True, alpha=0.3)
    ax1.legend(loc='best')
    
    # Plot 2: Request volume over time
    ax2.bar(times, totals, width=0.8, alpha=0.7, color='steelblue', label='Requests per bucket')
    if fault_window:
        ax2.axvspan(fault_window[0], fault_window[1], alpha=0.2, color='red')
    
    ax2.set_xlabel('Time (seconds)')
    ax2.set_ylabel('Request Count')
    ax2.set_title('Request Volume per Second')
    ax2.grid(True, alpha=0.3, axis='y')
    ax2.legend(loc='best')
    
    plt.tight_layout()
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"✓ Plot saved to: {output_file}")
    plt.close()

def print_summary(buckets):
    """Print summary statistics."""
    if not buckets:
        print("No data available")
        return
    
    success_rates = [b['success_rate'] for b in buckets.values()]
    total_requests = sum(b['total'] for b in buckets.values())
    total_success = sum(b['success'] for b in buckets.values())
    total_failed = sum(b['failed'] for b in buckets.values())
    
    print("\n" + "="*70)
    print("K6 ANALYSIS SUMMARY")
    print("="*70)
    print(f"Total requests:  {total_requests}")
    print(f"Successful:      {total_success} ({total_success/total_requests*100:.2f}%)")
    print(f"Failed:          {total_failed} ({total_failed/total_requests*100:.2f}%)")
    print(f"Time buckets:    {len(buckets)} (1-second windows)")
    print(f"Min success rate: {min(success_rates):.2f}%")
    print(f"Max success rate: {max(success_rates):.2f}%")
    print(f"Avg success rate: {np.mean(success_rates):.2f}%")
    print("="*70 + "\n")

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_k6_results.py <ndjson_file> [output_plot] [fault_start] [fault_end]")
        print("  ndjson_file:   Path to k6 NDJSON output (e.g., rq1_results.json)")
        print("  output_plot:   Output file path (default: k6_analysis.png)")
        print("  fault_start:   Fault window start time in seconds (default: 5)")
        print("  fault_end:     Fault window end time in seconds (default: 15)")
        sys.exit(1)
    
    ndjson_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else 'k6_analysis.png'
    fault_start = int(sys.argv[3]) if len(sys.argv) > 3 else 5
    fault_end = int(sys.argv[4]) if len(sys.argv) > 4 else 15
    
    print(f"Reading k6 NDJSON output from: {ndjson_file}")
    requests = parse_k6_ndjson(ndjson_file)
    
    if requests is None or len(requests) == 0:
        print("Error: No valid requests found in NDJSON file")
        sys.exit(1)
    
    print(f"Parsed {len(requests)} requests")
    
    # Bucket requests by time
    buckets = bucket_requests(requests, bucket_size_seconds=1)
    
    # Print summary
    print_summary(buckets)
    
    # Plot results
    plot_results(buckets, fault_window=(fault_start, fault_end), output_file=output_file)
    print(f"✓ Analysis complete!")

if __name__ == '__main__':
    main()
