import { mockInvoke } from './mock'

interface CefQuery {
  request: string
  persistent?: boolean
  onSuccess: (response: string) => void
  onFailure: (code: number, message: string) => void
}

declare global {
  interface Window {
    cefQuery?: (query: CefQuery) => number
    relict: { emit: (name: string, payload: unknown) => void }
  }
}

const listeners = new Map<string, Set<(payload: any) => void>>()

/** Subscribes to an event pushed from the game. Returns an unsubscribe function. */
export function on<T>(name: string, fn: (payload: T) => void): () => void {
  if (!listeners.has(name)) listeners.set(name, new Set())
  listeners.get(name)!.add(fn)
  return () => listeners.get(name)?.delete(fn)
}

// Called by the game through executeJavaScript.
window.relict = {
  emit(name, payload) {
    listeners.get(name)?.forEach((fn) => fn(payload))
  },
}

/** True when running inside the game; false in a normal browser (npm run dev), where a mock backend is used. */
export const inGame = location.protocol === 'file:'

export function invoke<T>(method: string, params: Record<string, unknown> = {}): Promise<T> {
  if (!window.cefQuery) {
    if (inGame) return Promise.reject(new Error('The game bridge is not available'))
    return mockInvoke(method, params) as Promise<T>
  }
  return new Promise<T>((resolve, reject) => {
    window.cefQuery!({
      request: JSON.stringify({ method, params }),
      onSuccess: (response) => resolve(JSON.parse(response) as T),
      onFailure: (_code, message) => reject(new Error(message)),
    })
  })
}
