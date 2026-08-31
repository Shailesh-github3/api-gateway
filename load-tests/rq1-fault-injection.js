import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // Run for 30 seconds total:
  // 0-5s: Baseline (healthy)
  // 5-15s: Fault window (you will inject latency here)
  // 15-30s: Recovery window (you will remove latency here)
  duration: '30s',
  vus: 2, // 2 concurrent virtual users
};

const API_KEY =  'sk_live_REDACTED_3'; // REPLACE THIS

export default function () {
  const params = {
    headers: { 'X-API-Key': API_KEY },
    // CRITICAL: Timeout must be shorter than the Toxiproxy latency (5000ms).
    // If set to 2000ms, k6 will mark the request as failed (timeout) after 2s,
    // preventing the test from hanging.
    timeout: '2000',
  };

  const res = http.get('http://localhost:8080/v1/example-resource', params);

  // Check if the request succeeded (200 OK)
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  // Small delay to simulate realistic user think time and prevent overwhelming local Docker
  sleep(0.1);
}