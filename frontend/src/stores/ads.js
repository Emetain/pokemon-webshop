import { reactive } from 'vue'
import { shopApi } from '../services/api'

/**
 * Ads from Google AdSense on the free card and news pages. Off unless the server says otherwise (`ADS=adsense` with
 * a publisher id and an ad unit). Nothing from Google is loaded until a page with an ad opens, so someone who only
 * shops never talks to Google. Google's script asks visitors here for consent itself, with the message set up in the
 * AdSense account (Privacy & messaging).
 */
const SCRIPT_URL = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js'

const state = reactive({
  enabled: false,
  client: null,
  slots: {},
})

let loading = null
let scriptAdded = false

/**
 * The server sends `enabled: false` to a Premium member (phase 12). The answer is kept for the whole visit, so the ad
 * places also check the session themselves: someone who logs in with Premium sees no ads from then on.
 */
function load() {
  loading ??= shopApi
    .ads()
    .then((ads) => {
      state.enabled = Boolean(ads?.enabled && ads.client)
      state.client = ads?.client ?? null
      state.slots = ads?.slots ?? {}
    })
    .catch(() => {
      // No ads then. The page works without them.
    })
  return loading
}

/** Adds Google's script once. In development nothing is loaded: the ad places show a box instead. */
function ensureScript() {
  if (scriptAdded || !state.enabled || import.meta.env.DEV) return
  scriptAdded = true
  const script = document.createElement('script')
  script.async = true
  script.crossOrigin = 'anonymous'
  script.src = `${SCRIPT_URL}?client=${encodeURIComponent(state.client)}`
  document.head.appendChild(script)
}

/** Opens Google's consent message again, so a visitor can change their choice. */
function showChoices() {
  window.googlefc = window.googlefc || {}
  window.googlefc.callbackQueue = window.googlefc.callbackQueue || []
  window.googlefc.callbackQueue.push(() => window.googlefc.showRevocationMessage?.())
  ensureScript()
}

export function useAds() {
  return { state, load, ensureScript, showChoices }
}
