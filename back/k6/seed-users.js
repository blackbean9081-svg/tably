import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 1000);
const PASSWORD = 'password123!';

const seedFailed = new Counter('seed_failed');

export const options = {
  scenarios: {
    seed: {
      executor: 'shared-iterations',
      vus: Math.min(50, USERS),
      iterations: USERS,
      maxDuration: '10m',
    },
  },
  thresholds: {
    seed_failed: ['count==0'],
  },
};

export function userEmail(n) {
  return `loadtest-${String(n).padStart(4, '0')}@tably.com`;
}

export default function () {
  const n = exec.scenario.iterationInTest + 1;
  const res = http.post(`${BASE_URL}/api/members/signup`, JSON.stringify({
    email: userEmail(n),
    password: PASSWORD,
    name: `부하테스트${String(n).padStart(4, '0')}`,
  }), { headers: { 'Content-Type': 'application/json' } });

  const duplicated = res.status === 409
    && (res.json('error.code') === 'DUPLICATE_EMAIL');
  if (res.status !== 201 && !duplicated) {
    seedFailed.add(1);
    console.error(`가입 실패 [${userEmail(n)}] status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
}
