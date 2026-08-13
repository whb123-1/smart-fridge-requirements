import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: () => import('../layouts/AuthLayout.vue'),
      children: [{ path: '', component: () => import('../views/AuthView.vue') }],
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: [
        { path: '', component: () => import('../views/Fridge3DView.vue') },
        { path: 'fridge', component: () => import('../views/Fridge3DView.vue') },
        { path: 'inventory', component: () => import('../views/InventoryView.vue') },
        { path: 'zones', component: () => import('../views/ZonesView.vue') },
        { path: 'recipes', component: () => import('../views/RecipesView.vue') },
        { path: 'recipes/:id', component: () => import('../views/RecipeDetailView.vue') },
        { path: 'diet', component: () => import('../views/DietView.vue') },
        { path: 'reminders', component: () => import('../views/RemindersView.vue') },
        { path: 'stats', component: () => import('../views/StatsView.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/'
  }
})

export default router
