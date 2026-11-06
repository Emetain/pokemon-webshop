const dateFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'long',
  timeZone: 'Europe/Amsterdam',
})

const dateTimeFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Amsterdam',
})

const moneyFormats = new Map()

function moneyFormat(currency) {
  if (!moneyFormats.has(currency)) {
    moneyFormats.set(
      currency,
      new Intl.NumberFormat('nl-NL', { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }),
    )
  }
  return moneyFormats.get(currency)
}

const rateFormat = new Intl.NumberFormat('nl-NL', { maximumFractionDigits: 4 })

const numberFormat = new Intl.NumberFormat('nl-NL')

export function formatDate(instant) {
  return dateFormat.format(new Date(instant))
}

export function formatDateTime(instant) {
  return dateTimeFormat.format(new Date(instant))
}

export function formatMoney(cents, currency) {
  return moneyFormat(currency).format(cents / 100)
}

export function formatRate(rate) {
  return rateFormat.format(rate)
}

export function formatNumber(number) {
  return numberFormat.format(number)
}
