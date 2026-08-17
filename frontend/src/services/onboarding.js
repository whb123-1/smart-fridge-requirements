import { MAX_ZONES, MIN_ZONES, ZONE_KINDS } from '../components/fridgeLayouts.js'

const SUGGESTED_NAMES = Object.freeze(['冷藏区', '保鲜区', '变温区', '冷冻区', '扩展冷藏区', '扩展保鲜区'])

export function clampSensorCount(value) {
  return Math.max(0, Math.min(4, Number(value) || 0))
}

export function createZoneDrafts(count) {
  const total = Math.max(MIN_ZONES, Math.min(MAX_ZONES, Number(count) || 4))
  return Array.from({ length: total }, (_, index) => ({
    kind: ZONE_KINDS[index].toUpperCase(),
    name: SUGGESTED_NAMES[index],
    temperatureSensorCount: 1,
    humiditySensorCount: index === 3 ? 0 : 1,
  }))
}

export function validateOnboardingDraft(fridgeName, zones) {
  if (!String(fridgeName || '').trim()) return { valid: false, message: '请填写冰箱名称' }
  if (!Array.isArray(zones) || zones.length < MIN_ZONES || zones.length > MAX_ZONES) return { valid: false, message: '请选择 3-6 个分区' }
  const names = zones.map(zone => String(zone.name || '').trim().toLocaleLowerCase())
  if (names.some(name => !name)) return { valid: false, message: '请补全分区名称' }
  if (new Set(names).size !== names.length) return { valid: false, message: '分区名称不能重复' }
  const sensorsValid = zones.every(zone => ['temperatureSensorCount', 'humiditySensorCount'].every(field => {
    const value = Number(zone[field])
    return Number.isInteger(value) && value >= 0 && value <= 4
  }))
  return sensorsValid ? { valid: true, message: '' } : { valid: false, message: '传感器数量必须为 0-4' }
}
