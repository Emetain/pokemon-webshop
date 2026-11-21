/**
 * Starts downloading a page's code as soon as a finger or the mouse is on a link to it, or the keyboard focus, so a lot
 * of the waiting is already done when the click comes (2026-11-21). After the first page, the pages visitors open most
 * are fetched in the background when the browser has nothing else to do, unless the visitor asked to save data.
 */
const MOST_OPENED = ['/kaarten/pokemon', '/kaarten/pokemon/x/1', '/product/x', '/collectie', '/zoeken', '/winkelwagen']

function loadComponents(router, path, done) {
  if (done.has(path)) return
  done.add(path)
  for (const record of router.resolve(path).matched) {
    const component = record.components?.default
    // A lazy page is `() => import(...)`; once loaded the router keeps the component itself instead.
    if (typeof component === 'function' && component.length === 0) {
      Promise.resolve(component()).catch(() => done.delete(path))
    }
  }
}

export function prefetchPages(router) {
  const done = new Set()
  const onIntent = (event) => {
    const link = event.target instanceof Element ? event.target.closest('a[href]') : null
    if (!link || link.origin !== window.location.origin || link.target === '_blank') return
    loadComponents(router, link.pathname, done)
  }
  document.addEventListener('pointerover', onIntent, { passive: true })
  document.addEventListener('touchstart', onIntent, { passive: true })
  document.addEventListener('focusin', onIntent)

  if (navigator.connection?.saveData) return
  const idle = window.requestIdleCallback ?? ((callback) => setTimeout(callback, 2000))
  router.isReady().then(() => idle(() => MOST_OPENED.forEach((path) => loadComponents(router, path, done))))
}
