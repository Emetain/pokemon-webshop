import { formatNumber } from './format'
import { t } from './i18n'

/** A text from the catalog, or `fallback` when there is none. Sources add new values without warning. */
function textOr(key, fallback) {
  const text = t(key)
  return text === key ? fallback : text
}

export const seriesLabel = (series) => textOr(`catalog.series.${series}`, series)

export const variantLabel = (variant) => textOr(`catalog.variants.${variant}`, variant)

export const categoryLabel = (category) => textOr(`catalog.categories.${category}`, category)

export const colorLabel = (color) => textOr(`catalog.colors.${color}`, color)

/** What a card's colour is called in its game: energy type, ink or colour. */
export const colorFilterLabel = (game) => textOr(`catalog.filters.colorLabels.${game}`, t('catalog.filters.color'))

export function cardCountLabel(count) {
  return count === 1 ? t('catalog.oneCard') : t('catalog.cardCount', { count: formatNumber(count) })
}

export const dexLabel = (number) => `#${String(number).padStart(3, '0')}`

/** The URL query for a list of cards. Empty values stay out, and leaving out the page starts at page 1. */
export function filterQuery(values) {
  return Object.fromEntries(
    Object.entries(values)
      .filter(([, value]) => value)
      .map(([name, value]) => [name, String(value)]),
  )
}

/** The page number in a URL, which starts at 1. Anything unreadable means the first page. */
export function pageFromQuery(value) {
  return Math.max(Number.parseInt(value, 10) || 1, 1)
}
