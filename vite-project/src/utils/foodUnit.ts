// 根据食材名称推断合适的计量单位，避免出现"鸡肉 1 个"这种错误
export function inferFoodUnit(name: string): { unit: string; unitType: string } {
  const n = (name || '').trim()
  if (!n) {
    return { unit: '个', unitType: 'count' }
  }
  if (/奶|汁|饮料|酒|汤|油/.test(n)) {
    return { unit: '毫升', unitType: 'volume' }
  }
  if (/肉|鸡|猪|牛|羊|鱼|虾|蟹|贝|米|面|粉|糖|盐|酱|醋|豆腐/.test(n)) {
    return { unit: '克', unitType: 'weight' }
  }
  if (/蛋|苹果|橙|梨|桃|香蕉|土豆|番茄|黄瓜|辣椒|茄子|柠檬|西瓜|玉米/.test(n)) {
    return { unit: '个', unitType: 'count' }
  }
  return { unit: '个', unitType: 'count' }
}

// 每种计量类型允许的单位
export const UNIT_OPTIONS: Record<string, string[]> = {
  weight: ['克', '千克', '斤', '公斤', '块', '片', '条', '袋', '包'],
  volume: ['毫升', '升', '瓶', '盒', '杯', '袋', '包'],
  count: ['个', '根', '只', '块', '包', '盒', '袋', '瓶', '把', '头', '条'],
}

export function isUnitCompatible(unit: string, unitType: string): boolean {
  return (UNIT_OPTIONS[unitType] || []).includes(unit)
}
