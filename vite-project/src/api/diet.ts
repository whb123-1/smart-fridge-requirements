import request from './request'

export const listDietRecords = (params?: Record<string, any>) =>
  request.get('/diet/records', { params })
export const addDietRecord = (data: Record<string, any>) => request.post('/diet/records', data)
export const deleteDietRecord = (id: number) => request.delete(`/diet/records/${id}`)
export const dietSummary = (params?: Record<string, any>) =>
  request.get('/diet/summary', { params })
