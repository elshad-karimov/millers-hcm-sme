import { useCallback, useSyncExternalStore } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ALL_TILES, findTile, type Tile } from './modules'

/**
 * Favorites + Recents for the Navigator / Home springboard.
 *
 * Stored in localStorage, namespaced per user so two accounts on the same
 * browser don't share pins.  Only the route string is persisted; labels and
 * icons are always resolved back through modules.tsx (single source of truth),
 * so a renamed app updates everywhere automatically.
 */

type Listener = () => void
type Store = {
  get: () => string[]
  set: (next: string[]) => void
  subscribe: (l: Listener) => () => void
}

const stores: Record<string, Store> = {}

function load(key: string): string[] {
  try {
    const raw = localStorage.getItem(key)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string') : []
  } catch {
    return []
  }
}

function storeFor(key: string, cap: number): Store {
  const existing = stores[key]
  if (existing) return existing
  let value = load(key)
  const listeners = new Set<Listener>()
  const store: Store = {
    get: () => value,
    set: (next) => {
      value = next.slice(0, cap)
      try {
        localStorage.setItem(key, JSON.stringify(value))
      } catch {
        /* quota / private mode — pins just won't persist */
      }
      listeners.forEach((l) => l())
    },
    subscribe: (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
  }
  stores[key] = store
  return store
}

function useRoutes(key: string, cap: number) {
  const store = storeFor(key, cap)
  const routes = useSyncExternalStore(store.subscribe, store.get, store.get)
  return { routes, store }
}

const tilesFor = (routes: string[]): Tile[] =>
  routes.map((to) => ALL_TILES.find((t) => t.to === to)).filter(Boolean) as Tile[]

/** Pinned apps the user has starred. */
export function useFavorites() {
  const { user } = useAuth()
  const { routes, store } = useRoutes(`hcm.nav.fav.${user?.username ?? 'anon'}`, 30)
  const isFav = useCallback((to: string) => routes.includes(to), [routes])
  const toggle = useCallback(
    (to: string) => {
      const cur = store.get()
      store.set(cur.includes(to) ? cur.filter((x) => x !== to) : [to, ...cur])
    },
    [store],
  )
  return { favorites: tilesFor(routes), isFav, toggle }
}

/** Most-recently-visited apps, newest first. */
export function useRecents() {
  const { user } = useAuth()
  const { routes, store } = useRoutes(`hcm.nav.recent.${user?.username ?? 'anon'}`, 8)
  const record = useCallback(
    (pathname: string) => {
      const tile = findTile(pathname)
      if (!tile) return
      const cur = store.get()
      if (cur[0] === tile.to) return
      store.set([tile.to, ...cur.filter((x) => x !== tile.to)])
    },
    [store],
  )
  return { recents: tilesFor(routes), record }
}
