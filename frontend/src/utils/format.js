const dateFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'long',
  timeZone: 'Europe/Amsterdam',
})

const dateTimeFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Amsterdam',
})

const shortDateFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'medium',
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

const percentFormat = new Intl.NumberFormat('nl-NL', { maximumFractionDigits: 1 })

export function formatDate(instant) {
  return dateFormat.format(new Date(instant))
}

export function formatShortDate(instant) {
  return shortDateFormat.format(new Date(instant))
}

export function formatDateTime(instant) {
  return dateTimeFormat.format(new Date(instant))
}

export function formatMoney(cents, currency) {
  return moneyFormat(currency).format(cents / 100)
}

/** Without cents, for the numbers along a chart. */
export function formatWholeMoney(cents, currency) {
  return new Intl.NumberFormat('nl-NL', {
    style: 'currency',
    currency,
    currencyDisplay: 'narrowSymbol',
    maximumFractionDigits: 0,
  }).format(cents / 100)
}

export function formatRate(rate) {
  return rateFormat.format(rate)
}

export function formatNumber(number) {
  return numberFormat.format(number)
}

export function formatPercent(percent) {
  return `${percentFormat.format(percent)}%`
}

const monthFormat = new Intl.DateTimeFormat('nl-NL', { month: 'long', year: 'numeric', timeZone: 'Europe/Amsterdam' })
const weekdayFormat = new Intl.DateTimeFormat('nl-NL', { weekday: 'short', timeZone: 'Europe/Amsterdam' })
const shortMonthFormat = new Intl.DateTimeFormat('nl-NL', { month: 'short', timeZone: 'Europe/Amsterdam' })
const dayFormat = new Intl.DateTimeFormat('nl-NL', { day: 'numeric', timeZone: 'Europe/Amsterdam' })

/** "november 2026", for a date such as `2026-11-28`. */
export function formatMonth(date) {
  return monthFormat.format(new Date(date))
}

/** The parts of a date stamp: "za", "28", "nov". */
export function dateParts(date) {
  const value = new Date(date)
  return {
    weekday: weekdayFormat.format(value).replace('.', ''),
    day: dayFormat.format(value),
    month: shortMonthFormat.format(value).replace('.', ''),
  }
}

/** Whole days from today (in Dutch time) to a date such as `2026-11-28`. Negative for a date that passed. */
export function daysUntil(date, now = new Date()) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Amsterdam' }).format(now)
  return Math.round((Date.parse(date) - Date.parse(today)) / 86_400_000)
}

/**
 * An amount typed by a customer as cents: "12,50", "12.50", "€ 1.234,50" and "12" all work. With both a point and a
 * comma, the last one is the decimal separator. Null for anything that isn't an amount.
 */
export function parseMoneyInput(text) {
  const compact = String(text ?? '').replace(/[€$\s]/g, '').replace(/^US/i, '')
  if (!compact) return null
  const lastComma = compact.lastIndexOf(',')
  const lastPoint = compact.lastIndexOf('.')
  const decimal = lastComma > lastPoint ? ',' : '.'
  const thousands = decimal === ',' ? '.' : ','
  const normal = compact.split(thousands).join('').replace(decimal, '.')
  if (!/^\d+(\.\d{1,2})?$/.test(normal)) return null
  return Math.round(Number(normal) * 100)
}
