export function authenticatedLanding(onboardingRequired, user = null) {
  if (user?.passwordChangeRequired) return '/password-change'
  if (user?.role === 'ADMIN') return '/admin/users'
  return onboardingRequired ? '/onboarding' : '/app/home'
}

function landingRoute(path) {
  if (path === '/password-change') return { name: 'password-change' }
  if (path === '/admin/users') return { name: 'admin' }
  if (path === '/onboarding') return { name: 'onboarding' }
  return { name: 'app', params: { page: 'home' } }
}

export function resolveRouteAccess(to, currentSession) {
  if (!currentSession.authenticated) return to.meta.public ? true : { name: 'login' }
  const landing = authenticatedLanding(currentSession.onboardingRequired, currentSession.user)
  if (currentSession.user?.passwordChangeRequired && to.name !== 'password-change') return { name: 'password-change' }
  if (!currentSession.user?.passwordChangeRequired && to.name === 'password-change') return landingRoute(landing)
  if (to.meta.requiresAdmin && currentSession.user?.role !== 'ADMIN') return { name: 'app', params: { page: 'home' } }
  if (to.name === 'login') {
    return landingRoute(landing)
  }
  if (currentSession.user?.role === 'ADMIN' && to.name === 'onboarding') return { name: 'admin' }
  if (currentSession.user?.role !== 'ADMIN' && currentSession.onboardingRequired && to.name !== 'onboarding') return { name: 'onboarding' }
  if (!currentSession.onboardingRequired && to.name === 'onboarding') {
    return { name: 'app', params: { page: 'home' } }
  }
  return true
}
