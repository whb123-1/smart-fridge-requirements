import request from './request'

export const consumptionStats = (period: string) =>
  request.get('/stats/consumption', { params: { period } })
export const summaryStats = () => request.get('/stats/summary')
