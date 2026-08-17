import test from 'node:test'
import assert from 'node:assert/strict'
import { clampSensorCount, createZoneDrafts, validateOnboardingDraft } from './onboarding.js'

test('onboarding drafts stay between three and six zones', () => {
  assert.equal(createZoneDrafts(1).length, 3)
  assert.equal(createZoneDrafts(9).length, 6)
})

test('zero sensor zones are valid', () => {
  const zones = createZoneDrafts(3).map(zone => ({ ...zone, temperatureSensorCount: 0, humiditySensorCount: 0 }))
  assert.equal(validateOnboardingDraft('我的冰箱', zones).valid, true)
  assert.equal(clampSensorCount(-2), 0)
  assert.equal(clampSensorCount(9), 4)
})

test('duplicate zone names are rejected', () => {
  const zones = createZoneDrafts(3)
  zones[1].name = zones[0].name
  assert.equal(validateOnboardingDraft('我的冰箱', zones).message, '分区名称不能重复')
})
