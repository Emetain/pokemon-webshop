import { computed, watch } from 'vue'
import { useSession } from './session'

/**
 * Dark mode and colour themes (phase 12). Light, dark or the device's setting is free for everyone; the colour themes
 * other than Poketopia's own are Premium, and the server only sends one while Premium lasts. Both live on the account
 * and are put on <html> as data-mode and data-theme, which the tokens in main.css follow. The last choice is also kept
 * in this browser, for public/appearance.js, which applies it before the page is drawn.
 */
const STORAGE_KEY = 'appearance'
const DEFAULT = { mode: 'LIGHT', theme: 'POKETOPIA' }

export const COLOR_MODES = ['LIGHT', 'DARK', 'SYSTEM']
export const COLOR_THEMES = ['POKETOPIA', 'FIRE', 'GRASS', 'ELECTRIC', 'PSYCHIC']

const session = useSession()
const deviceDark = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null

const chosen = computed(() => {
  const user = session.state.user
  return user ? { mode: user.colorMode ?? DEFAULT.mode, theme: user.colorTheme ?? DEFAULT.theme } : DEFAULT
})

function store(choice) {
  try {
    if (choice.mode === DEFAULT.mode && choice.theme === DEFAULT.theme) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, JSON.stringify(choice))
  } catch {
    // Then the page starts light and changes once the app knows who is logged in.
  }
}

function apply(choice) {
  const root = document.documentElement
  const dark = choice.mode === 'DARK' || (choice.mode === 'SYSTEM' && Boolean(deviceDark?.matches))
  if (dark) root.dataset.mode = 'dark'
  else delete root.dataset.mode
  if (choice.theme && choice.theme !== 'POKETOPIA') root.dataset.theme = choice.theme.toLowerCase()
  else delete root.dataset.theme
}

// Waits for the session, so a visitor whose choice is already on the page (appearance.js) doesn't flash light.
watch(
  () => [session.state.loaded, chosen.value.mode, chosen.value.theme],
  () => {
    if (!session.state.loaded) return
    apply(chosen.value)
    store(chosen.value)
  },
  { immediate: true },
)

// "Zoals je apparaat" follows the device when it switches, for example at sunset.
deviceDark?.addEventListener?.('change', () => apply(chosen.value))

export function useAppearance() {
  return { chosen }
}
