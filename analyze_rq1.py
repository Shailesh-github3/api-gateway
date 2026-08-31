#!/usr/bin/env python3
"""
RQ1 Analysis: Traffic Control Under Fault Injection
Parses k6 NDJSON output, analyzes availability/correctness under Redis latency fault.
"""

import json
import sys
from collections import defaultdict
from datetime import datetime, timedelta
import numpy as np

def parse_k6_ndjson(file_path):
    """
    Parse k6 NDJSON output.
    Extracts HTTP requests with timestamp and status code.
    Returns list of dicts: {timestamp, status, duration_ms}
    """
    requests = []
    
    try:
        with open(file_path, 'r') as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    obj = json.loads(line)
                    
                    # Look for http_reqs metric (one per request)
                    if obj.get('metric') == 'http_reqs':
                        data = obj.get('data', {})
                        tags = data.get('tags', {})
                        time_str = obj.get('time', '')
                        
                        status = int(tags.get('status', 0))
                        duration_ms = data.get('time', 0)
                        
                        # Parse ISO 8601 timestamp
                        try:
                            if time_str:
                                ts = datetime.fromisoformat(time_str.replace('Z', '+00:00'))
                                requests.append({
                                    'timestamp': ts,
                                    'status': status,
                                    'duration_ms': duration_ms
                                })
                        except:
                            pass
                except json.JSONDecodeError:
                    pass
    
    except FileNotFoundError:
        print(f"Error: {file_path} not found", file=sys.stderr)
        return None
    
    return requests

def bucket_requests_by_window(requests, window_seconds=1):
    """
    Bucket requests into 1-second windows.
    Returns dict: {bucket_index: {'success': count, 'failed': count, 'statuses': {status: count}}}
    """
    if not requests:
        return {}
    
    requests = sorted(requests, key=lambda x: x['timestamp'])
    start_time = requests[0]['timestamp']
    
    buckets = defaultdict(lambda: {'success': 0, 'failed': 0, 'statuses': defaultdict(int)})
    
    for req in requests:
        elapsed = (req['timestamp'] - start_time).total_seconds()
        bucket_idx = int(elapsed / window_seconds)
        
        status = req['status']
        buckets[bucket_idx]['statuses'][status] += 1
        
        if status == 200:
            buckets[bucket_idx]['success'] += 1
        else:
            buckets[bucket_idx]['failed'] += 1
    
    return dict(sorted(buckets.items()))

def calculate_statistics(requests, buckets):
    """Calculate summary statistics."""
    if not requests:
        return None
    
    # Baseline: first 5 seconds (before fault)
    baseline_requests = [r for r in requests if (r['timestamp'] - requests[0]['timestamp']).total_seconds() < 5]
    baseline_success_rate = len([r for r in baseline_requests if r['status'] == 200]) / len(baseline_requests) if baseline_requests else 0
    
    # Fault window: 5-15 seconds
    fault_requests = [r for r in requests if 5 <= (r['timestamp'] - requests[0]['timestamp']).total_seconds() < 15]
    fault_failure_rate = 1 - (len([r for r in fault_requests if r['status'] == 200]) / len(fault_requests)) if fault_requests else 0
    
    # Time to first failure after fault injection
    time_to_first_failure = None
    start_time = requests[0]['timestamp']
    for req in requests:
        elapsed = (req['timestamp'] - start_time).total_seconds()
        if 5 <= elapsed < 15 and req['status'] != 200:
            time_to_first_failure = elapsed - 5  # seconds after fault injection
            break
    
    # Recovery window: 15-30 seconds
    recovery_requests = [r for r in requests if 15 <= (r['timestamp'] - requests[0]['timestamp']).total_seconds() <= 30]
    recovery_success_rate = len([r for r in recovery_requests if r['status'] == 200]) / len(recovery_requests) if recovery_requests else 0
    
    # Unique status codes during fault window
    fault_statuses = set(r['status'] for r in fault_requests)
    
    return {
        'baseline_success_rate': baseline_success_rate,
        'fault_failure_rate': fault_failure_rate,
        'time_to_first_failure': time_to_first_failure,
        'recovery_success_rate': recovery_success_rate,
        'fault_statuses': sorted(fault_statuses),
        'total_requests': len(requests),
        'baseline_requests': len(baseline_requests),
        'fault_requests': len(fault_requests),
        'recovery_requests': len(recovery_requests)
    }

def print_summary(stats):
    """Print analysis summary."""
    if not stats:
        return
    
    print("\n" + "="*80)
    print("RQ1 ANALYSIS SUMMARY: Traffic Control Under Fault Injection")
    print("="*80)
    print(f"\nBaseline Phase (0-5s, healthy Redis):")
    print(f"  Success rate:    {stats['baseline_success_rate']*100:.1f}%")
    print(f"  Requests:        {stats['baseline_requests']}")
    
    print(f"\nFault Injection Phase (5-15s, 5000ms latency):")
    print(f"  Failure rate:    {stats['fault_failure_rate']*100:.1f}%")
    print(f"  Requests:        {stats['fault_requests']}")
    print(f"  HTTP statuses:   {stats['fault_statuses']}")
    print(f"  Time to 1st fail: {stats['time_to_first_failure']:.2f}s after injection" if stats['time_to_first_failure'] else "  Time to 1st fail: No failures observed")
    
    print(f"\nRecovery Phase (15-30s, latency removed):")
    print(f"  Success rate:    {stats['recovery_success_rate']*100:.1f}%")
    print(f"  Requests:        {stats['recovery_requests']}")
    
    print(f"\nTotal Requests: {stats['total_requests']}")
    print("="*80)

def generate_markdown_report(stats):
    """Generate Markdown report section."""
    markdown = "## Results Summary\n\n"
    
    markdown += "### Failure Behavior\n"
    markdown += f"- **HTTP statuses during fault**: {', '.join(map(str, stats['fault_statuses']))}\n"
    
    if 0 in stats['fault_statuses']:
        fail_mode = "**fail-closed** (requests timeout/are rejected)"
    elif 500 in stats['fault_statuses']:
        fail_mode = "**fail-open with errors** (returns 500)"
    elif 429 in stats['fault_statuses']:
        fail_mode = "**fail-open** (rate limit exceeded, 429)"
    else:
        fail_mode = "**unknown** (check statuses)"
    
    markdown += f"- **Failure mode**: {fail_mode}\n"
    
    markdown += "\n### Key Metrics\n"
    markdown += f"| Metric | Value |\n"
    markdown += f"|--------|-------|\n"
    markdown += f"| Baseline success rate | {stats['baseline_success_rate']*100:.1f}% |\n"
    markdown += f"| Fault window failure rate | {stats['fault_failure_rate']*100:.1f}% |\n"
    markdown += f"| Time to first failure (after injection) | {stats['time_to_first_failure']:.2f}s |\n"
    markdown += f"| Recovery success rate | {stats['recovery_success_rate']*100:.1f}% |\n"
    
    markdown += "\n### Interpretation\n"
    if 0 in stats['fault_statuses']:
        markdown += f"The system exhibits **fail-closed behavior**: when Redis becomes unavailable, requests "
        markdown += f"timeout (status 0) rather than returning stale or default values. This is the correct behavior "
        markdown += f"for a payment gateway API, where **unavailability is preferable to inconsistency**.\n"
    else:
        markdown += f"The system exhibits mixed behavior with status codes {stats['fault_statuses']}. "
        markdown += f"Recovery is observed after fault removal at {stats['recovery_success_rate']*100:.1f}% success rate.\n"
    
    markdown += "\n### Is Fail-Closed Right for Payment Gateways?\n"
    markdown += "Yes. For payment processing systems, fail-closed (reject transactions when state cannot be verified) is the correct "
    markdown += "choice over fail-open (allow transactions with stale data). Returning 5xx/0 timeout rather than accepting a payment under "
    markdown += "uncertainty prevents double-charging, lost funds, or fraud. The slight availability hit is an acceptable trade-off for correctness. "
    markdown += "Operators can mitigate downtime through Redis replication, monitoring, and graceful degradation (e.g., cached tiers) without compromising "
    markdown += "the core fail-closed principle.\n"
    
    return markdown

def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_rq1.py <ndjson_file> [output_png]")
        sys.exit(1)
    
    ndjson_file = sys.argv[1]
    output_png = sys.argv[2] if len(sys.argv) > 2 else 'rq1_availability.png'
    
    print(f"Parsing k6 NDJSON output from: {ndjson_file}")
    requests = parse_k6_ndjson(ndjson_file)
    
    if requests is None or len(requests) == 0:
        print("Error: No valid requests found in NDJSON file", file=sys.stderr)
        sys.exit(1)
    
    print(f"[OK] Parsed {len(requests)} requests")
    
    # Bucket by time window
    buckets = bucket_requests_by_window(requests, window_seconds=1)
    print(f"[OK] Bucketed into {len(buckets)} one-second windows")
    
    # Calculate statistics
    stats = calculate_statistics(requests, buckets)
    print_summary(stats)
    
    # Generate Markdown
    markdown = generate_markdown_report(stats)
    print("\n" + markdown)
    
    # Save Markdown report
    report_file = 'rq1_report_snippet.md'
    with open(report_file, 'w') as f:
        f.write(markdown)
    print(f"\n[OK] Report saved to: {report_file}")
    
    # Generate plot
    try:
        import matplotlib.pyplot as plt
        import matplotlib.patches as mpatches
        
        indices = sorted(buckets.keys())
        times = np.array(indices)
        success_rates = np.array([100 * buckets[idx]['success'] / (buckets[idx]['success'] + buckets[idx]['failed']) 
                                 if (buckets[idx]['success'] + buckets[idx]['failed']) > 0 else 0 
                                 for idx in indices])
        totals = np.array([buckets[idx]['success'] + buckets[idx]['failed'] for idx in indices])
        
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))
        
        # Plot 1: Success rate
        ax1.plot(times, success_rates, marker='o', linewidth=2.5, markersize=6, 
                color='steelblue', label='Success Rate')
        ax1.axhline(y=100, color='green', linestyle='--', alpha=0.3, linewidth=1)
        ax1.axhline(y=0, color='red', linestyle='--', alpha=0.3, linewidth=1)
        ax1.axvspan(5, 15, alpha=0.2, color='red', label='Fault Injection Window (5-15s)')
        
        ax1.set_xlabel('Time (seconds)', fontsize=11)
        ax1.set_ylabel('Success Rate (%)', fontsize=11)
        ax1.set_title('RQ1: API Availability During Redis Latency Fault', fontsize=13, fontweight='bold')
        ax1.set_ylim(-5, 105)
        ax1.grid(True, alpha=0.3)
        ax1.legend(loc='best', fontsize=10)
        
        # Plot 2: Request volume
        ax2.bar(times, totals, width=0.8, alpha=0.7, color='steelblue')
        ax2.axvspan(5, 15, alpha=0.2, color='red')
        ax2.set_xlabel('Time (seconds)', fontsize=11)
        ax2.set_ylabel('Request Count per 1s Window', fontsize=11)
        ax2.set_title('Request Volume', fontsize=12, fontweight='bold')
        ax2.grid(True, alpha=0.3, axis='y')
        
        plt.tight_layout()
        plt.savefig(output_png, dpi=300, bbox_inches='tight')
        print(f"[OK] Chart saved to: {output_png}")
        plt.close()
    
    except ImportError:
        print("[WARN] matplotlib not available - skipping chart generation")
        print("  Install with: pip install matplotlib")

if __name__ == '__main__':
    main()
