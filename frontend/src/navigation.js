export function authenticatedLanding(onboardingRequired) {
  return onboardingRequired ? '/onboarding' : '/app/home'
}

export function resolveRouteAccess(to, currentSession) {
  if (!currentSession.authenticated) return to.meta.public ? true : { name: 'login' }
  if (to.name === 'login') {
    return currentSession.onboardingRequired
      ? { name: 'onboarding' }
      : { name: 'app', params: { page: 'home' } }
  }
  if (currentSession.onboardingRequired && to.name !== 'onboarding') return { name: 'onboarding' }
  if (!currentSession.onboardingRequired && to.name === 'onboarding') {
    return { name: 'app', params: { page: 'home' } }
  }
  return true
}
