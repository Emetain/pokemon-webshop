import { reactive, readonly } from 'vue'

/**
 * Between a click and the next page (2026-11-21, the owner's wish). The next page may still have to be downloaded, or
 * wait for a check, and the old page standing still made it look like the click did nothing. When that takes longer
 * than a moment, the layout swaps the old page for a skeleton of the next one and a bar runs along the top.
 *
 * Only for another page: filters, sorting and loading more change the query of the same page and show their own
 * loading. The skeleton comes from the route's `meta.skeleton`: a kind of `PageSkeleton.vue`, or `{ kind, band }`.
 */
const SHOW_AFTER_MS = 120

const state = reactive({ slow: false, skeleton: { kind: 'article', band: false } })
let timer = null

function skeletonOf(route) {
  const value = route.meta.skeleton ?? (route.path.startsWith('/admin') ? 'table' : 'article')
  return typeof value === 'string' ? { kind: value, band: false } : { band: false, ...value }
}

export function startNavigation(to, from) {
  if (from.matched.length === 0 || to.path === from.path) return
  clearTimeout(timer)
  // The back button: the browser already shows the entry it goes back to, and the router puts its scroll position back.
  const goingBack = window.history.state?.current === to.fullPath
  const skeleton = skeletonOf(to)
  timer = setTimeout(() => {
    state.skeleton = skeleton
    state.slow = true
    if (!goingBack) window.scrollTo({ top: 0 })
  }, SHOW_AFTER_MS)
}

export function endNavigation() {
  clearTimeout(timer)
  timer = null
  state.slow = false
}

export function useNavigation() {
  return readonly(state)
}
