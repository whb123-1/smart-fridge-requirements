import request from './request'

export const listReminders = (status?: string) =>
  request.get('/reminders', { params: { status } })
export const unreadCount = () => request.get('/reminders/unread-count')
export const markReminderRead = (id: number) => request.patch(`/reminders/${id}/read`)
export const dismissReminder = (id: number) => request.post(`/reminders/${id}/dismiss`)
