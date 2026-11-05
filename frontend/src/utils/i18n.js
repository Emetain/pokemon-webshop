import nl from '../data/i18n/nl'

const messages = nl

function lookup(key) {
  return key.split('.').reduce((node, part) => node?.[part], messages)
}

/** Reads a text from the catalog. `{name}` placeholders are filled from `params`. */
export function t(key, params = {}) {
  const value = lookup(key)
  if (value === undefined) return key
  if (typeof value !== 'string') return value
  return value.replace(/\{(\w+)\}/g, (match, name) => params[name] ?? match)
}
