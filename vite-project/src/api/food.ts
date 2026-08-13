import request from './request'

export const listFoods = (params: Record<string, any>) => request.get('/foods', { params })
export const listCategories = () => request.get('/foods/categories')
export const listEstimates = () => request.get('/foods/estimates')
export const addFood = (data: Record<string, any>) => request.post('/foods', data)
export const updateFood = (id: number, data: Record<string, any>) => request.put(`/foods/${id}`, data)
export const deleteFood = (id: number) => request.delete(`/foods/${id}`)
export const consumeFood = (id: number, data: Record<string, any>) =>
  request.post(`/foods/${id}/consume`, data)
export const expireFood = (id: number) => request.post(`/foods/${id}/expire`)
export const discardFood = (id: number, remark?: string) =>
  request.post(`/foods/${id}/discard`, null, { params: { remark } })
