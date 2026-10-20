// Hash routes, so a refresh on any screen works on a static host:
// #/  #/c/<name>  #/c/<name>/pathway  #/c/<name>/builder  #/glossary  #/credits
import { useEffect, useState } from 'react'

export type Route =
  | { kind: 'gallery' }
  | { kind: 'glossary' }
  | { kind: 'credits' }
  | { kind: 'character'; name: string; screen: 'sheet' | 'pathway' | 'builder' }

export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '')
  if (path === '/glossary') return { kind: 'glossary' }
  if (path === '/credits') return { kind: 'credits' }
  const m = /^\/c\/([^/]+)(?:\/(pathway|builder))?$/.exec(path)
  if (m) {
    try {
      return { kind: 'character', name: decodeURIComponent(m[1]), screen: (m[2] as 'pathway' | 'builder' | undefined) ?? 'sheet' }
    } catch {
      return { kind: 'gallery' }
    }
  }
  return { kind: 'gallery' }
}

export function hashOf(route: Route): string {
  switch (route.kind) {
    case 'gallery':
      return '#/'
    case 'glossary':
      return '#/glossary'
    case 'credits':
      return '#/credits'
    case 'character':
      return `#/c/${encodeURIComponent(route.name)}${route.screen === 'sheet' ? '' : `/${route.screen}`}`
  }
}

/** Where Android's BackHandler goes from each screen. */
export function parentOf(route: Route): Route {
  return route.kind === 'character' && route.screen !== 'sheet' ? { ...route, screen: 'sheet' } : { kind: 'gallery' }
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(location.hash))
  useEffect(() => {
    const on = () => setRoute(parseRoute(location.hash))
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return route
}

/** Forward: a new history entry, which the browser's own back undoes. */
export function go(route: Route) {
  const depth = ((history.state as { depth?: number } | null)?.depth ?? 0) + 1
  history.pushState({ depth }, '', hashOf(route))
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}

/** An in-app back arrow: the browser's back when we came from the parent, else replace. */
export function back(from: Route) {
  if (((history.state as { depth?: number } | null)?.depth ?? 0) > 0) {
    history.back()
    return
  }
  history.replaceState({ depth: 0 }, '', hashOf(parentOf(from)))
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}

/** Replaces the entry: a route that names no character falls back to the list. */
export function replace(route: Route) {
  history.replaceState(history.state, '', hashOf(route))
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}
