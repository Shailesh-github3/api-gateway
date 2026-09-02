import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 2,
  iterations: 20,
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEY_A = __ENV.KEY_A;
const KEY_B = __ENV.KEY_B;

if (!KEY_A || !KEY_B) {
  throw new Error('KEY_A and KEY_B environment variables are required. Create two keys via POST /admin/api-keys first.');
}

export default function () {
  const key = __VU === 1 ? KEY_A : KEY_B;

  const res = http.get(`${BASE_URL}/v1/example-resource`, {
    headers: { 'X-API-Key': key },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
