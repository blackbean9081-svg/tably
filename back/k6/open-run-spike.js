import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 1000);
const SLOTS = Number(__ENV.SLOTS || 20);
const RESTAURANT_ID = Number(__ENV.RESTAURANT_ID || 1);
const WARMUP_S = Number(__ENV.WARMUP_S || 30);
const PASSWORD = 'password123!';

const loginFailed = new Counter('login_failed');
const holdSuccess = new Counter('hold_success');
const holdConflict = new Counter('hold_conflict');
const holdUnexpected = new Counter('hold_unexpected');

export const options = {
  scenarios: {
    openrun: {
      executor: 'per-vu-iterations',
      vus: USERS,
      iterations: 1,
      maxDuration: `${WARMUP_S + 120}s`,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    login_failed: ['count==0'],
    hold_unexpected: ['count==0'],
    hold_success: [`count<=${SLOTS}`],
    'http_req_duration{operation:hold}': ['p(95)>=0'],
    'http_req_duration{operation:login}': ['p(95)>=0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function userEmail(n) {
  return `loadtest-${String(n).padStart(4, '0')}@tably.com`;
}

function login(email) {
  const res = http.post(`${BASE_URL}/api/members/login`, JSON.stringify({
    email,
    password: PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { operation: 'login' },
  });
  return res.status === 200 ? res.json('data.accessToken') : null;
}

function defaultStartDate() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() + 1, 1);
}

export function setup() {
  const token = login(userEmail(1));
  if (!token) {
    throw new Error('setup 로그인 실패 — seed-users.js를 먼저 실행했는지, 서버가 떠 있는지 확인');
  }

  const start = __ENV.DATE ? new Date(__ENV.DATE) : defaultStartDate();
  const slotIds = [];
  for (let d = 0; d < 60 && slotIds.length < SLOTS; d++) {
    const date = new Date(start.getTime() + d * 86400000).toISOString().slice(0, 10);
    const res = http.get(
      `${BASE_URL}/api/restaurants/${RESTAURANT_ID}/slots?date=${date}`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    if (res.status !== 200) continue;
    for (const slot of res.json('data') || []) {
      if (slot.status === 'OPEN' && slotIds.length < SLOTS) slotIds.push(slot.id);
    }
  }
  if (slotIds.length < SLOTS) {
    throw new Error(`OPEN 슬롯이 ${slotIds.length}개뿐 (목표 ${SLOTS}) — 시드 슬롯 생성 여부·RESTAURANT_ID·DATE 확인`);
  }

  const spikeAt = Date.now() + WARMUP_S * 1000;
  console.log(`대상 슬롯 ${slotIds.length}개: [${slotIds.join(', ')}] — ${WARMUP_S}초 뒤 스파이크`);
  return { slotIds, spikeAt };
}

export default function (data) {
  sleep(Math.random() * WARMUP_S * 0.5);

  const token = login(userEmail(((__VU - 1) % USERS) + 1));
  if (!token) {
    loginFailed.add(1);
    return;
  }

  const waitS = (data.spikeAt - Date.now()) / 1000;
  if (waitS > 0) sleep(waitS);

  const slotId = data.slotIds[(__VU - 1) % data.slotIds.length];
  const res = http.post(`${BASE_URL}/api/reservations`, JSON.stringify({
    slotId,
    partySize: 2,
  }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    tags: { operation: 'hold' },
  });

  if (res.status === 201) {
    holdSuccess.add(1);
  } else if (res.status === 409 && res.json('error.code') === 'SLOT_ALREADY_TAKEN') {
    holdConflict.add(1);
  } else {
    holdUnexpected.add(1);
    console.error(`예상 밖 응답 [slot=${slotId}] status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
}
