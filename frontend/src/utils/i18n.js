import en from '../data/i18n/en'
import nl from '../data/i18n/nl'
import { language } from '../stores/language'

/**
 * The texts of the website. Dutch is complete; English (2026-11-21) has every text a customer sees. Anything English
 * doesn't have, such as the admin area, falls back to Dutch.
 */
const catalogs = { nl, en }
const messages = catalogs[language] ?? nl

function lookup(catalog, key) {
  return key.split('.').reduce((node, part) => node?.[part], catalog)
}

/** Reads a text from the catalog. `{name}` placeholders are filled from `params`. */
export function t(key, params = {}) {
  const value = lookup(messages, key) ?? lookup(nl, key)
  if (value === undefined) return key
  if (typeof value !== 'string') return value
  return value.replace(/\{(\w+)\}/g, (match, name) => params[name] ?? match)
}
