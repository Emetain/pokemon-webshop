import { computed, reactive, watch } from 'vue'
import { COUNTRY_CODES, DEFAULT_COUNTRY } from '../data/countries'
import { useSession } from './session'

const STORAGE_KEY = 'region.country'

function readStoredCountry() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return COUNTRY_CODES.includes(stored) ? stored : null
  } catch {
    return null
  }
}

function storeCountry(country) {
  try {
    localStorage.setItem(STORAGE_KEY, country)
  } catch {
    // The choice then only lasts until the page closes.
  }
}

const state = reactive({
  country: readStoredCountry() ?? DEFAULT_COUNTRY,
})

const session = useSession()

function applyCountry(country) {
  state.country = country
  storeCountry(country)
}

// The account is leading: logging in brings the saved country along.
watch(
  () => session.state.user?.country,
  (country) => {
    if (country) applyCountry(country)
  },
)

async function setCountry(country) {
  if (session.isLoggedIn.value) {
    await session.updateProfile({ country })
  }
  applyCountry(country)
}

const country = computed(() => state.country)

export function useRegion() {
  return { country, setCountry }
}
