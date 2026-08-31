#!/usr/bin/env python3
"""
Generate mock RQ2a timing data (CSV format) for demonstration.
Simulates String.equals() (vulnerable) vs MessageDigest.isEqual() (constant-time).
"""

import csv
import random
import numpy as np

def generate_mock_rq2a_data(output_file='timing_results_mock.csv', iterations_per_position=100):
    """
    Generate mock timing measurements.
    
    String.equals(): Varies by position (vulnerable to timing attack)
    MessageDigest.isEqual(): Constant across positions (safe)
    """
    
    num_positions = 32  # SHA-256 hash size
    
    with open(output_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['method', 'byte_position', 'execution_time_ns'])
        
        # String.equals() - VULNERABLE: varies by position
        for pos in range(num_positions):
            # Early positions fail quickly, late positions take longer
            # This simulates early-exit behavior in naive comparison
            base_time = 500 + (pos * 150)  # Scales with position
            
            for _ in range(iterations_per_position):
                # Add random noise
                time_ns = int(base_time + random.gauss(0, 50))
                time_ns = max(100, time_ns)  # Clamp to positive
                
                writer.writerow(['String.equals()', pos, time_ns])
        
        # MessageDigest.isEqual() - SAFE: constant across positions
        base_time = 2000  # Constant-time implementation takes longer but is constant
        
        for pos in range(num_positions):
            for _ in range(iterations_per_position):
                # Minimal variation - all positions take ~same time
                time_ns = int(base_time + random.gauss(0, 30))
                time_ns = max(100, time_ns)
                
                writer.writerow(['MessageDigest.isEqual()', pos, time_ns])
    
    print(f"✓ Generated mock RQ2a data: {output_file}")
    print(f"  - String.equals(): {num_positions * iterations_per_position} measurements (vulnerable)")
    print(f"  - MessageDigest.isEqual(): {num_positions * iterations_per_position} measurements (safe)")

if __name__ == '__main__':
    generate_mock_rq2a_data()
