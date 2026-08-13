import request from './request'

export const recommendRecipes = (params?: Record<string, any>) =>
  request.get('/recipes/recommend', { params })
export const recipeDetail = (id: number) => request.get(`/recipes/${id}`)
export const generateRecipe = (data: Record<string, any>) => request.post('/recipes/generate', data)
export const checkSelectedRecipes = (data: Record<string, any>) =>
  request.post('/recipes/check-selected', data)
export const favoriteRecipe = (id: number) => request.post(`/recipes/${id}/favorite`)
export const unfavoriteRecipe = (id: number) => request.delete(`/recipes/${id}/favorite`)
export const favoriteList = () => request.get('/recipes/favorites/list')
export const historyList = () => request.get('/recipes/history/list')
export const scaleRecipe = (id: number, data: Record<string, any>) =>
  request.post(`/recipes/${id}/scale`, data)
export const cookRecipe = (id: number, data: Record<string, any>) =>
  request.post(`/recipes/${id}/cook`, data)
