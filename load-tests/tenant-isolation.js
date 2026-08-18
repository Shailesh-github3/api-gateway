import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 2, // Two virtual users
  iterations: 20, // 10 requests per user
};

const KEY_A = 'sk_live_s0Ab5HJNz6B9fTSVkyRPi0cRazAitzKp'; // First tenant
const KEY_B = 'sk_live_dkYxU77v8TbC4X9q4ashEvPlAUWl82FS'; // Second tenant

export default function () {
  // Each VU uses a different key
  const key = __VU === 1 ? KEY_A : KEY_B;

  const res = http.get('http://localhost:8080/v1/example-resource', {
    headers: { 'X-API-Key': key },
  });

  // Both tenants should get 200 for their first 10 requests
  // (burst capacity is 10 per key, not shared)
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}