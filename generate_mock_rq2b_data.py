#!/usr/bin/env python3
"""
Generate mock RQ2b data - scale testing with different numbers of API keys.
Tests latency impact of key store size: 100, 10K, 1M keys.
"""

import csv
import random
import numpy as np

def generate_scale_data(output_file='scale_test_results_mock.csv'):
    """
    Generate mock scale test measurements.
    Simulates latency increase as API key store grows from 100 to 1M keys.
    """
    
    scale_configs = {
        100: {
            'p50': 50,      # milliseconds
            'p95': 80,
            'p99': 120,
            'std': 20
        },
        10000: {
            'p50': 55,
            'p95': 100,
            'p99': 180,
            'std': 25
        },
        1000000: {
            'p50': 70,
            'p95': 150,
            'p99': 300,
            'std': 35
        }
    }
    
    with open(output_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['scale', 'requests_per_second', 'latency_ms', 'throughput_rps', 'success_rate'])
        
        # Generate 100 requests per scale factor
        for scale, config in scale_configs.items():
            for _ in range(100):
                # Latency roughly follows a distribution with given p50/p95/p99
                # Use the p50 as base with some distribution
                base_latency = config['p50']
                latency = base_latency + random.gauss(0, config['std'])
                latency = max(1, latency)  # Clamp to minimum
                
                # Throughput roughly inverse of latency
                throughput = 1000.0 / latency  # RPS (rough estimate)
                throughput = max(1, throughput)
                
                # Success rate high for all scales
                success_rate = random.gauss(99.0, 0.5)
                success_rate = max(99.0, min(100.0, success_rate))
                
                writer.writerow([scale, '100', f'{latency:.1f}', f'{throughput:.1f}', f'{success_rate:.2f}'])
    
    print(f"[OK] Generated mock RQ2b scale test data: {output_file}")
    print(f"  - Scale factors tested: 100, 10K, 1M API keys")
    print(f"  - Requests per scale: 100")

if __name__ == '__main__':
    generate_scale_data()
