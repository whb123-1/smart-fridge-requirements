import test from 'node:test'
import assert from 'node:assert/strict'
import { MAX_ZONES, MIN_ZONES, clampZoneCount, getFridgeSpec, getZoneLayout } from './fridgeLayouts.js'

test('clampZoneCount keeps the supported range and default', () => {
  assert.equal(clampZoneCount(-4), MIN_ZONES)
  assert.equal(clampZoneCount(8), MAX_ZONES)
  assert.equal(clampZoneCount(4.6), 5)
  assert.equal(clampZoneCount('not-a-number'), 4)
})

test('three-zone layout uses three full-width center doors', () => {
  const layout = getZoneLayout(3)
  assert.equal(layout.length, 3)
  assert.deepEqual(layout.map(({ id, side, width, height }) => ({ id, side, width, height })), [
    { id: 1, side: 'center', width: 4.42, height: 2.3 },
    { id: 2, side: 'center', width: 4.42, height: 2.3 },
    { id: 3, side: 'center', width: 4.42, height: 2.3 },
  ])
})

test('tall layouts fill the cabinet height with even sealed gaps', () => {
  const layout = getZoneLayout(6)
  const gasketOffset = 0.0275
  const first = layout[0]
  const middle = layout[2]
  const last = layout[4]

  assert.ok(Math.abs(first.y + first.height / 2 + gasketOffset - 4) < 1e-9)
  assert.ok(Math.abs(last.y - last.height / 2 - gasketOffset + 3.165) < 1e-9)
  assert.equal(Number((first.y - first.height / 2 - gasketOffset - (middle.y + middle.height / 2 + gasketOffset)).toFixed(3)), 0.05)
  assert.equal(Number((middle.y - middle.height / 2 - gasketOffset - (last.y + last.height / 2 + gasketOffset)).toFixed(3)), 0.05)
})

test('four-zone layout preserves the legacy two-by-two geometry', () => {
  const layout = getZoneLayout(4)
  assert.deepEqual(layout.map(({ id, centerX, y, width, height, hingeSide }) => ({ id, centerX, y, width, height, hingeSide })), [
    { id: 1, centerX: -1.17, y: 1.83, width: 2.24, height: 2.72, hingeSide: 'left' },
    { id: 2, centerX: 1.17, y: 1.83, width: 2.24, height: 2.72, hingeSide: 'right' },
    { id: 3, centerX: -1.17, y: -0.92, width: 2.24, height: 2.62, hingeSide: 'left' },
    { id: 4, centerX: 1.17, y: -0.92, width: 2.24, height: 2.62, hingeSide: 'right' },
  ])
})

test('five and six-zone layouts retain stable zone IDs and centered fifth door', () => {
  const five = getZoneLayout(5)
  const six = getZoneLayout(6)

  assert.deepEqual(five.map(zone => zone.id), [1, 2, 3, 4, 5])
  assert.equal(five[4].side, 'center')
  assert.equal(five[4].centerX, 0)
  assert.equal(five[4].width, 4.42)
  assert.ok(five.every(zone => zone.height === 2.3))
  assert.deepEqual(six.map(zone => zone.id), [1, 2, 3, 4, 5, 6])
  assert.deepEqual(six.map(zone => zone.side), ['left', 'right', 'left', 'right', 'left', 'right'])
  assert.ok(six.every(zone => zone.height === 2.3))
})

test('fridge specs use the tall body outside the legacy four-zone model', () => {
  const legacy = getFridgeSpec(4)
  const tall = getFridgeSpec(6)

  assert.equal(legacy.tall, false)
  assert.equal(legacy.interiorProfile, 'legacy')
  assert.equal(tall.tall, true)
  assert.equal(tall.bodyHeight, 7.15)
  assert.equal(tall.interiorProfile, 'compact')
  assert.equal(tall.layouts.length, 6)
})
