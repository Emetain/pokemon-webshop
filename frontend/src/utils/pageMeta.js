import { watchEffect } from 'vue'
import { t } from './i18n'

/**
 * The title, description, robots tag and canonical link of the page. The router sets them from the route's `meta`
 * (`title` and `description` are i18n keys, `noindex` keeps a page out of search engines). A view whose title comes
 * from its data calls [usePageMeta] once that has loaded.
 *
 * Search engines that run JavaScript read these. Link previews in WhatsApp and the like don't: the backend fills in
 * the tags of product, drop and news pages for them (see SeoController).
 */

/** A description of at most this many characters, as search results show. */
const DESCRIPTION_LENGTH = 160

function headElement(selector, create) {
  let element = document.head.querySelector(selector)
  if (!element) {
    element = create()
    document.head.appendChild(element)
  }
  return element
}

function setMeta(name, content) {
  const selector = `meta[name="${name}"]`
  if (!content) {
    document.head.querySelector(selector)?.remove()
    return
  }
  const element = headElement(selector, () => Object.assign(document.createElement('meta'), { name }))
  element.setAttribute('content', content)
}

function setCanonical(href) {
  if (!href) {
    document.head.querySelector('link[rel="canonical"]')?.remove()
    return
  }
  headElement('link[rel="canonical"]', () => Object.assign(document.createElement('link'), { rel: 'canonical' })).href = href
}

/** The address without filters and sorting. A page number stays, so every page of a list is its own page. */
function canonicalUrl() {
  const url = new URL(window.location.href)
  const page = Number(url.searchParams.get('page'))
  return `${url.origin}${url.pathname}${page > 1 ? `?page=${page}` : ''}`
}

/** Plain text of at most [DESCRIPTION_LENGTH] characters, cut at a word. */
export function shortText(text) {
  // The marks of news and info pages: headings, lists, bold and links.
  const plain = (text ?? '')
    .replace(/^\s*(#{1,6}|-)\s+/gm, '')
    .replace(/\*\*/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
  if (plain.length <= DESCRIPTION_LENGTH) return plain
  const cut = plain.slice(0, DESCRIPTION_LENGTH - 1)
  return `${cut.slice(0, cut.lastIndexOf(' ') > 80 ? cut.lastIndexOf(' ') : cut.length)}…`
}

/** `title` is the page's own name, without the shop's: "<title> | Poketopia". */
export function setPageMeta({ title, description, noindex = false } = {}) {
  document.title = title ? t('meta.title', { page: title }) : t('meta.siteTitle')
  setMeta('description', shortText(description) || t('meta.description'))
  setMeta('robots', noindex ? 'noindex' : null)
  setCanonical(noindex ? null : canonicalUrl())
}

/** For a view: [meta] returns `{ title, description, noindex }`, or null while the data is still loading. */
export function usePageMeta(meta) {
  watchEffect(() => {
    const value = meta()
    if (value) setPageMeta(value)
  })
}

/** From the route's `meta`. Called by the router after every navigation to another path. */
export function setRouteMeta(route) {
  const { description, noindex } = route.meta
  // Pages that have no content yet use `titleKey`, which they also show.
  const title = route.meta.title ?? route.meta.titleKey
  setPageMeta({ title: title ? t(title) : null, description: description ? t(description) : null, noindex })
}

/** After a change of the query only, such as page 2 of a list: the canonical link follows, the rest stays. */
export function updateCanonical() {
  const noindex = document.head.querySelector('meta[name="robots"]')?.content?.includes('noindex')
  setCanonical(noindex ? null : canonicalUrl())
}
