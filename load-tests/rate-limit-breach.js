import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1, // Single virtual user
  iterations: 15, // Fire 15 requests total
};

const API_KEY = 'sk_live_REDACTED';

export default function () {
  const res = http.get('http://localhost:8080/v1/example-resource', {
    headers: { 'X-API-Key': API_KEY },
  });

  // Assert: first 10 requests should be 200, 11th+ should be 429
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