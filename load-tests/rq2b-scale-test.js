import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 2,
  duration: '30s',
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;

if (!API_KEY) {
  throw new Error('API_KEY environment variable is required. Create a key via POST /admin/api-keys first.');
}

export default function () {
  const params = {
    headers: {
      'X-API-Key': API_KEY,
    },
    timeout: '5000ms',
  };

  const res = http.get(`${BASE_URL}/v1/example-resource`, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 5000ms': (r) => r.timings.duration < 5000,
  });

  sleep(0.1);
}
