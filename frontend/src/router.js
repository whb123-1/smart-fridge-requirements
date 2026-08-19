import { createRouter, createWebHistory } from 'vue-router'
import { resolveRouteAccess } from './navigation.js'
import { restoreSession, session } from './session.js'

const EmptyRoute = { render: () => null }

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app/home' },
    { path: '/login', name: 'login', component: () => import('./views/AuthView.vue'), meta: { public: true } },
    { path: '/password-change', name: 'password-change', component: () => import('./views/PasswordChangeView.vue'), meta: { requiresAuth: true } },
    { path: '/onboarding', name: 'onboarding', component: () => import('./views/OnboardingView.vue'), meta: { requiresAuth: true } },
    { path: '/admin/users', name: 'admin', component: () => import('./views/AdminView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/app/:page?', name: 'app', component: EmptyRoute, meta: { requiresAuth: true, requiresOnboarding: true } },
    { path: '/:pathMatch(.*)*', redirect: '/app/home' },
  ],
})

router.beforeEach(async to => {
  await restoreSession()
  return resolveRouteAccess(to, session)
})
