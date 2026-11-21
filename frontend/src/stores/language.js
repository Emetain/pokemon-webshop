/**
 * The language of the website (2026-11-21): Dutch, the shop's own and the default, or English. Chosen with the switch
 * in the top bar and kept in this browser. Fixed for a page load: switching reloads the page, so every text, date and
 * amount on it changes at once, also the ones a page worked out when it opened. Mails and the pages the shop writes
 * itself (info pages, news, product texts) stay Dutch for now; the admin area too.
 */
export const LANGUAGES = ['nl', 'en']
export const DEFAULT_LANGUAGE = 'nl'

const STORAGE_KEY = 'language'

function stored() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return LANGUAGES.includes(value) ? value : DEFAULT_LANGUAGE
  } catch {
    return DEFAULT_LANGUAGE
  }
}

export const language = stored()

/** For Intl: dates and amounts the Dutch way (12,50) or the British way (12.50), both with euros and day-month. */
export const formatLocale = language === 'en' ? 'en-GB' : 'nl-NL'

if (typeof document !== 'undefined') document.documentElement.lang = language

export function setLanguage(next) {
  if (!LANGUAGES.includes(next) || next === language) return
  try {
    if (next === DEFAULT_LANGUAGE) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // Without storage the choice can't last; the page stays as it is.
    return
  }
  window.location.reload()
}
