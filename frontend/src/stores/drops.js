import { computed, reactive } from 'vue'
import { dropApi } from '../services/api'

/** Live drops, for the LIVE badge in the menu. Asks once a minute while the shop is open. */
const state = reactive({ live: [], started: false })

const REFRESH_MS = 60_000

async function load() {
  try {
    state.live = await dropApi.live()
  } catch {
    // The badge simply doesn't show.
  }
}

function start() {
  if (state.started) return
  state.started = true
  load()
  setInterval(load, REFRESH_MS)
}

export function useLiveDrops() {
  return {
    live: computed(() => state.live),
    isLive: computed(() => state.live.length > 0),
    start,
    load,
  }
}
