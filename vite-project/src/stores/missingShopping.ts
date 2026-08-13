import { defineStore } from 'pinia'

export const useMissingShoppingStore = defineStore('missingShopping', {
  state: () => ({
    items: [] as any[],
  }),
  actions: {
    set(items: any[]) {
      this.items = items
    },
    clear() {
      this.items = []
    },
  },
})
