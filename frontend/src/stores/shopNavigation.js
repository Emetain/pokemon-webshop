import { ref } from 'vue'
import { shopApi } from '../services/api'

// Per game: the series from the card catalog, newest first. Loaded once per visit.
const seriesByGame = ref({})
const loading = new Map()

/** The series of a game, for the menu and the shop pages. Empty until they arrive, and when they can't. */
function loadSeries(game) {
  if (!loading.has(game)) {
    loading.set(
      game,
      shopApi
        .series(game)
        .then((series) => {
          seriesByGame.value = { ...seriesByGame.value, [game]: series }
        })
        .catch(() => {
          // The menu works without series. The next page tries again.
          loading.delete(game)
        }),
    )
  }
  return loading.get(game)
}

export function useShopNavigation() {
  return { seriesByGame, loadSeries }
}
