import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  iterations: 15,
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;

if (!API_KEY) {
  throw new Error('API_KEY environment variable is required. Create a key via POST /admin/api-keys first.');
}

export default function () {
  const res = http.get(`${BASE_URL}/v1/example-resource`, {
    headers: { 'X-API-Key': API_KEY },
  });

  if (__ITER < 10) {
    check(res, {
      'status is 200': (r) => r.status === 200,
    });
  } else {
    check(res, {
      'status is 429 after burst': (r) => r.status === 429,
      'Retry-After header present': (r) => r.headers['Retry-After'] !== undefined,
    });
  }
}
