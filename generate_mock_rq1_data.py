#!/usr/bin/env python3
"""
Generate mock RQ1 test data (k6 NDJSON format) for demonstration.
Simulates baseline, fault injection, and recovery phases.
"""

import json
from datetime import datetime, timedelta
import random

def generate_mock_rq1_data(output_file='rq1_results_mock.json', num_vus=2, duration_sec=30):
    """
    Generate mock k6 NDJSON output simulating RQ1 test.
    
    Timeline:
    - 0-5s: Baseline (95% success, 200ms avg latency)
    - 5-15s: Fault window (5% success, status 0 timeouts, 2000ms+)
    - 15-30s: Recovery (90% success, 200ms avg latency)
    """
    
    base_time = datetime.fromisoformat('2026-08-31T14:00:00+00:00')
    request_id = 0
    
    with open(output_file, 'w') as f:
        current_time = base_time
        
        for elapsed_sec in range(duration_sec):
            # Requests per second per VU (roughly 10 req/s per VU)
            requests_per_window = random.randint(8, 12)
            
            for i in range(requests_per_window):
                request_id += 1
                
                # Determine phase and probability of success
                if elapsed_sec < 5:
                    # Baseline: 95% success
                    success_prob = 0.95
                    latency = random.gauss(200, 50)
                    latency = max(100, min(1000, latency))  # Clamp 100-1000ms
                elif elapsed_sec < 15:
                    # Fault window: 5% success, mostly timeouts
                    success_prob = 0.05
                    latency = 2000 if random.random() > success_prob else random.gauss(200, 50)
                else:
                    # Recovery: 90% success
                    success_prob = 0.90
                    latency = random.gauss(200, 50)
                    latency = max(100, min(1000, latency))
                
                # Determine status
                if random.random() < success_prob:
                    status = 200
                else:
                    # During fault: timeouts (status 0)
                    status = 0
                
                # Generate request timestamp (spread throughout the second)
                req_time = current_time + timedelta(milliseconds=random.randint(0, 900))
                
                # k6 http_reqs metric
                metric_obj = {
                    'type': 'Point',
                    'metric': 'http_reqs',
                    'data': {
                        'tags': {
                            'name': 'http://localhost:8080/v1/example-resource',
                            'status': str(status),
                            'method': 'GET'
                        },
                        'time': int(latency),
                        'value': 1.0
                    },
                    'time': req_time.isoformat()
                }
                
                f.write(json.dumps(metric_obj) + '\n')
            
            current_time += timedelta(seconds=1)
    
    print(f"✓ Generated mock RQ1 data: {output_file} ({request_id} requests)")

if __name__ == '__main__':
    generate_mock_rq1_data()
