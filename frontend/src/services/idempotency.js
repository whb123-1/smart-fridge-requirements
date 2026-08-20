export function createIdempotencyKey(cryptoProvider = globalThis.crypto) {
  try {
    if (typeof cryptoProvider?.randomUUID === 'function') return cryptoProvider.randomUUID()
  } catch {
    // Some mobile webviews expose randomUUID but reject it outside a secure context.
  }
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2) || '0'
  return `idempotency-${timestamp}-${random}`
}

export function createSessionIdempotencyKeyStore(storageKey, options = {}) {
  let storage = null
  if ('storage' in options) storage = options.storage
  else {
    try { storage = globalThis.sessionStorage } catch { storage = null }
  }
  const keyFactory = options.keyFactory || createIdempotencyKey
  let memoryKey = ''

  return {
    get() {
      if (memoryKey) return memoryKey
      try { memoryKey = storage?.getItem(storageKey) || '' } catch { memoryKey = '' }
      if (!memoryKey) {
        memoryKey = keyFactory()
        try { storage?.setItem(storageKey, memoryKey) } catch { /* Keep the key in memory for this page. */ }
      }
      return memoryKey
    },
    clear() {
      memoryKey = ''
      try { storage?.removeItem(storageKey) } catch { /* Storage is optional. */ }
    },
  }
}
