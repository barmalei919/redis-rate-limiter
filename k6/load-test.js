import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    cheap_traffic: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      exec: 'cheap',
    },
    expensive_traffic: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 20,
      exec: 'expensive',
    },
    auth_traffic: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 30,
      exec: 'auth',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export function cheap() {
  const userId = `user-${Math.floor(Math.random() * 20)}`;
  const res = http.get(`${BASE}/api/cheap/items`, {
    headers: { 'X-User-Id': userId },
  });
  check(res, { 'status 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

export function expensive() {
  const userId = `user-${Math.floor(Math.random() * 5)}`;
  const res = http.get(`${BASE}/api/expensive/report`, {
    headers: { 'X-User-Id': userId },
  });
  check(res, { 'status 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

export function auth() {
  const res = http.post(`${BASE}/api/auth/login`, null, {
    headers: { 'X-Forwarded-For': randomIp() },
  });
  check(res, { 'status 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

function randomIp() {
  return `10.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}
