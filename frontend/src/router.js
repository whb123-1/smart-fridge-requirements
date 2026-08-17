import { createRouter, createWebHistory } from 'vue-router'
import AuthView from './views/AuthView.vue'
import OnboardingView from './views/OnboardingView.vue'
import { resolveRouteAccess } from './navigation.js'
import { restoreSession, session } from './session.js'

const EmptyRoute = { render: () => null }

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app/home' },
    { path: '/login', name: 'login', component: AuthView, meta: { public: true } },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView, meta: { requiresAuth: true } },
    { path: '/app/:page?', name: 'app', component: EmptyRoute, meta: { requiresAuth: true, requiresOnboarding: true } },
    { path: '/:pathMatch(.*)*', redirect: '/app/home' },
  ],
})

router.beforeEach(async to => {
  await restoreSession()
  return resolveRouteAccess(to, session)
})
