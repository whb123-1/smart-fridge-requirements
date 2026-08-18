export const MIN_ZONES = 3
export const MAX_ZONES = 6

export const ZONE_KINDS = Object.freeze(['chill', 'fresh', 'variable', 'freeze', 'chill', 'fresh'])

export function clampZoneCount(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return 4
  return Math.min(MAX_ZONES, Math.max(MIN_ZONES, Math.round(numeric)))
}

const legacyFour = [
  { id: 1, side: 'left', hingeSide: 'left', panelCenterX: 1.13, centerX: -1.17, y: 1.83, width: 2.24, height: 2.72, kind: 'chill' },
  { id: 2, side: 'right', hingeSide: 'right', panelCenterX: -1.13, centerX: 1.17, y: 1.83, width: 2.24, height: 2.72, kind: 'fresh' },
  { id: 3, side: 'left', hingeSide: 'left', panelCenterX: 1.13, centerX: -1.17, y: -0.92, width: 2.24, height: 2.62, kind: 'variable' },
  { id: 4, side: 'right', hingeSide: 'right', panelCenterX: -1.13, centerX: 1.17, y: -0.92, width: 2.24, height: 2.62, kind: 'freeze' },
]

// These centers and door height fill the tall cabinet between its top and bottom rails.
const tallRows = [2.8225, 0.4175, -1.9875]
const TALL_DOOR_HEIGHT = 2.3
const FULL_WIDTH_DOOR = 4.42

function tallDoor(id, centerX, y, options = {}) {
  const side = options.side || (centerX < 0 ? 'left' : centerX > 0 ? 'right' : 'center')
  return {
    id,
    side,
    hingeSide: options.hingeSide || (side === 'right' ? 'right' : 'left'),
    centerX,
    y,
    width: options.width ?? 2.24,
    height: options.height ?? TALL_DOOR_HEIGHT,
    kind: options.kind || ZONE_KINDS[id - 1],
  }
}

export function getZoneLayout(count) {
  const normalized = clampZoneCount(count)
  if (normalized === 4) return legacyFour.map(layout => ({ ...layout }))

  if (normalized === 3) {
    return [
      tallDoor(1, 0, tallRows[0], { width: FULL_WIDTH_DOOR, side: 'center', hingeSide: 'left', kind: 'chill' }),
      tallDoor(2, 0, tallRows[1], { width: FULL_WIDTH_DOOR, side: 'center', hingeSide: 'left', kind: 'fresh' }),
      tallDoor(3, 0, tallRows[2], { width: FULL_WIDTH_DOOR, side: 'center', hingeSide: 'left', kind: 'variable' }),
    ]
  }

  if (normalized === 5) {
    return [
      tallDoor(1, -1.17, tallRows[0], { side: 'left', hingeSide: 'left', kind: 'chill' }),
      tallDoor(2, 1.17, tallRows[0], { side: 'right', hingeSide: 'right', kind: 'fresh' }),
      tallDoor(3, -1.17, tallRows[1], { side: 'left', hingeSide: 'left', kind: 'variable' }),
      tallDoor(4, 1.17, tallRows[1], { side: 'right', hingeSide: 'right', kind: 'freeze' }),
      tallDoor(5, 0, tallRows[2], { width: FULL_WIDTH_DOOR, side: 'center', hingeSide: 'left', kind: 'chill' }),
    ]
  }

  return [
    tallDoor(1, -1.17, tallRows[0], { side: 'left', hingeSide: 'left', kind: 'chill' }),
    tallDoor(2, 1.17, tallRows[0], { side: 'right', hingeSide: 'right', kind: 'fresh' }),
    tallDoor(3, -1.17, tallRows[1], { side: 'left', hingeSide: 'left', kind: 'variable' }),
    tallDoor(4, 1.17, tallRows[1], { side: 'right', hingeSide: 'right', kind: 'freeze' }),
    tallDoor(5, -1.17, tallRows[2], { side: 'left', hingeSide: 'left', kind: 'chill' }),
    tallDoor(6, 1.17, tallRows[2], { side: 'right', hingeSide: 'right', kind: 'fresh' }),
  ]
}

export function getFridgeSpec(count) {
  const normalized = clampZoneCount(count)
  const tall = normalized !== 4
  const bodyHeight = tall ? 7.15 : 5.56
  const bodyCenterY = 0.4
  const bodyTop = bodyCenterY + bodyHeight / 2
  const bodyBottom = bodyCenterY - bodyHeight / 2

  return {
    count: normalized,
    tall,
    bodyWidth: 4.92,
    bodyHeight,
    bodyCenterY,
    bodyTop,
    bodyBottom,
    shellTopY: bodyTop + 0.16,
    shellBottomY: bodyBottom - 0.16,
    displayY: bodyTop + 0.24,
    feetY: bodyBottom - 0.39,
    groundY: bodyBottom - 0.38,
    interiorProfile: normalized === 4 ? 'legacy' : normalized === 3 ? 'wide' : 'compact',
    layouts: getZoneLayout(normalized),
  }
}

export function bindZonesToFridgeSpec(zones) {
  const zoneList = Array.isArray(zones) ? zones : []
  const spec = getFridgeSpec(zoneList.length)

  return {
    ...spec,
    layouts: spec.layouts.map((layout, index) => {
      const zone = zoneList[index]
      return {
        ...layout,
        zoneId: zone?.id ?? null,
        kind: zone?.kind || layout.kind,
      }
    }),
  }
}
