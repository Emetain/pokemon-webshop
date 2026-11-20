import { reactive, readonly } from 'vue'

/**
 * The Premium popup (the owner's wish, 2026-11-20): a customer without Premium who tries a Premium feature gets this
 * popup with the plans, instead of being sent to a page. `feature` picks the heading and the line under it:
 * 'pregrade', 'history', 'scanner', or null for Premium in general. `message` is the server's own words, for a refusal
 * the frontend didn't see coming.
 *
 * It opens in three ways, so a new Premium feature gets it without extra work:
 * - a click on a Premium feature calls `openPremium(feature)`;
 * - a link to a page with `meta.premium` (or to /premium) opens it instead of the page, see router.js;
 * - every API answer `premium_required` opens it, see services/api.js.
 */
const state = reactive({ open: false, feature: null, message: '', returnPath: '' })

/** `returnPath`: where logging in from the popup leads back to, when that isn't the page it opened on. */
export function openPremium(feature = null, message = '', returnPath = '') {
  state.feature = feature
  state.message = message
  state.returnPath = returnPath
  state.open = true
}

/** For an API refusal: keeps the feature a view already named, and only adds the server's words. */
export function openPremiumForRefusal(message) {
  if (state.open) return
  openPremium(null, message)
}

export function closePremium() {
  state.open = false
}

export function usePremiumDialog() {
  return { state: readonly(state), openPremium, closePremium }
}
