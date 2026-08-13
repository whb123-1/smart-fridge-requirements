import request from './request'

export const listShoppingLists = () => request.get('/shopping/lists')
export const createShoppingList = (name?: string) =>
  request.post('/shopping/lists', null, { params: { name } })
export const autoShoppingList = () => request.post('/shopping/lists/auto')
export const deleteShoppingList = (id: number) => request.delete(`/shopping/lists/${id}`)
export const addShoppingItem = (listId: number, data: Record<string, any>) =>
  request.post(`/shopping/lists/${listId}/items`, data)
export const updateShoppingItem = (itemId: number, data: Record<string, any>) =>
  request.put(`/shopping/items/${itemId}`, data)
export const removeShoppingItem = (itemId: number) => request.delete(`/shopping/items/${itemId}`)
