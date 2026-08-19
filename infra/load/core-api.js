import http from 'k6/http'
import { check, fail } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const readDuration = new Trend('core_read_duration', true)
const writeDuration = new Trend('core_write_duration', true)
const errorRate = new Rate('core_error_rate')

export const options = {
  vus: 50,
  duration: __ENV.LOAD_DURATION || '60s',
  thresholds: {
    core_read_duration: ['p(95)<500'],
    core_write_duration: ['p(95)<800'],
    core_error_rate: ['rate<0.01'],
  },
  insecureSkipTLSVerify: __ENV.LOAD_INSECURE_TLS === 'true',
}

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '')
const loadPassword = __ENV.LOAD_PASSWORD || (__ENV.LOAD_PASSWORD_FILE ? open(__ENV.LOAD_PASSWORD_FILE).trim() : '')
const hostHeaders = __ENV.LOAD_HOST_HEADER ? { Host: __ENV.LOAD_HOST_HEADER } : {}
const preferenceBody = JSON.stringify({ preferences: [
  { type: 'EXPIRY_SOON', inAppEnabled: true, emailEnabled: false, timezone: 'Asia/Shanghai' },
  { type: 'EXPIRED', inAppEnabled: true, emailEnabled: false, timezone: 'Asia/Shanghai' },
  { type: 'LOW_STOCK', inAppEnabled: true, emailEnabled: false, timezone: 'Asia/Shanghai' },
  { type: 'ENVIRONMENT_ALERT', inAppEnabled: true, emailEnabled: false, timezone: 'Asia/Shanghai' },
] })

export function setup() {
  if (!baseUrl || !__ENV.LOAD_IDENTIFIER || !loadPassword) fail('BASE_URL, LOAD_IDENTIFIER and LOAD_PASSWORD or LOAD_PASSWORD_FILE are required')
  const login = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
    identifier: __ENV.LOAD_IDENTIFIER,
    password: loadPassword,
  }), { headers: { ...hostHeaders, 'Content-Type': 'application/json' } })
  const token = login.json('data.accessToken')
  if (!check(login, { 'load user login succeeds': response => response.status === 200 && Boolean(token) })) {
    fail(`load user login failed: HTTP ${login.status}, code=${login.json('code') || 'unknown'}`)
  }
  const headers = { ...hostHeaders, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': 'load-baseline-preferences-v1' }
  const baseline = http.put(`${baseUrl}/api/v1/me/notification-preferences`, preferenceBody, { headers })
  if (!check(baseline, { 'baseline write succeeds': response => response.status === 200 })) fail('baseline write failed')
  return { token }
}

export default function (data) {
  const headers = { ...hostHeaders, Authorization: `Bearer ${data.token}`, Accept: 'application/json' }
  const reads = http.batch([
    ['GET', `${baseUrl}/api/v1/fridges`, null, { headers }],
    ['GET', `${baseUrl}/api/v1/inventory/items`, null, { headers }],
  ])
  for (const response of reads) {
    readDuration.add(response.timings.duration)
    errorRate.add(response.status !== 200)
  }
  const write = http.put(`${baseUrl}/api/v1/me/notification-preferences`, preferenceBody, {
    headers: { ...headers, 'Content-Type': 'application/json', 'Idempotency-Key': 'load-baseline-preferences-v1' },
  })
  writeDuration.add(write.timings.duration)
  errorRate.add(write.status !== 200)
}
