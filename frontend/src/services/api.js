/**
 * 后端接入边界：页面只通过此模块访问远端数据。
 * 开发阶段使用 Promise + 本地数据；联调时将方法体替换为 fetch/axios 即可。
 */
const wait = (data, delay = 180) => new Promise(resolve => setTimeout(() => resolve(data), delay))

export const api = {
  login: credentials => wait({ token: 'mock-token', user: { name: credentials.account || '林知夏' } }),
  register: profile => wait({ token: 'mock-token', user: profile }),
  getDashboard: () => wait({ updatedAt: new Date().toISOString() }),
  addFood: food => wait({ ...food, id: Date.now() }),
  updateFood: food => wait(food),
  consumeFood: (id, amount) => wait({ id, amount }),
  updateZone: zone => wait(zone),
  updatePreferences: preferences => wait(preferences),
  toggleFavorite: recipeId => wait({ recipeId }),
  updateShoppingItem: item => wait(item),
}
