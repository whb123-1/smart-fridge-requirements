import request from './request'

export const getPreference = () => request.get('/preference')
export const updatePreference = (data: Record<string, any>) => request.put('/preference', data)
