import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 2,
  duration: '30s',
};

// Replace with actual API key from admin endpoint
const API_KEY = '__REPLACE_WITH_ACTUAL_KEY__';

// Simulates hitting key-verification at different DB sizes.
// Pass __ENV.KEY_COUNT to specify: 100, 10000, or 1000000
const KEY_COUNT = __ENV.KEY_COUNT || '100';

export default function () {
  const url = `http://localhost:8080/v1/verify-key?keyCount=${KEY_COUNT}`;
  const params = {
    headers: {
      'X-API-Key': API_KEY,
    },
    timeout: '5000ms',
  };

  const res = http.get(url, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 5000ms': (r) => r.timings.duration < 5000,
  });

  sleep(0.1);
}
