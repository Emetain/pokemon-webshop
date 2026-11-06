import { computed, ref } from 'vue'
import { DEFAULT_CURRENCY } from '../data/currencies'
import { catalogApi } from '../services/api'
import { formatMoney } from '../utils/format'
import { useSession } from './session'

const session = useSession()

// Per currency: { perEuro, date }. Empty until the rates arrive, or when there are none yet.
const rates = ref({})

let loading = null

function loadRates() {
  loading ??= catalogApi
    .exchangeRates()
    .then((list) => {
      rates.value = Object.fromEntries(list.map((rate) => [rate.currency, rate]))
    })
    .catch(() => {
      // Prices then stay in their own currency. The next page tries again.
      loading = null
    })
}

const displayCurrency = computed(() => session.state.user?.currency ?? DEFAULT_CURRENCY)

function perEuro(currency) {
  return currency === 'EUR' ? 1 : rates.value[currency]?.perEuro
}

/** The rate behind showing `currency` in the visitor's currency, or null when nothing gets converted. */
function conversionRate(currency) {
  if (currency === displayCurrency.value) return null
  return rates.value[currency === 'EUR' ? displayCurrency.value : currency] ?? null
}

/** Card prices only. Shop prices are always in euros, and you pay what you see. */
function formatPrice(cents, currency) {
  if (!conversionRate(currency)) return formatMoney(cents, currency)
  const converted = Math.round((cents * perEuro(displayCurrency.value)) / perEuro(currency))
  return formatMoney(converted, displayCurrency.value)
}

export function useCurrency() {
  loadRates()
  return { displayCurrency, conversionRate, formatPrice }
}
