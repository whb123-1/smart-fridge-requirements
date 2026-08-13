import request from './request'

export const listZones = () => request.get('/zones')
export const zoneAlerts = () => request.get('/zones/alerts')
export const createZone = (data: Record<string, any>) => request.post('/zones', data)
export const updateZone = (id: number, data: Record<string, any>) => request.put(`/zones/${id}`, data)
export const deleteZone = (id: number) => request.delete(`/zones/${id}`)
export const recordZone = (id: number, data: Record<string, any>) =>
  request.post(`/zones/${id}/records`, data)
export const zoneRecords = (id: number, params?: Record<string, any>) =>
  request.get(`/zones/${id}/records`, { params })
