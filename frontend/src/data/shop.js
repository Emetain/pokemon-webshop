import { GAMES } from './games'
import { t } from '../utils/i18n'

/** Every product type with its URL name, as in the menu. `navKey` is the plural label under `nav.products`. */
export const PRODUCT_TYPES = [
  { type: 'BOOSTER_BOX', slug: 'booster-boxen', navKey: 'boosterBox' },
  { type: 'ELITE_TRAINER_BOX', slug: 'elite-trainer-boxen', navKey: 'eliteTrainerBox' },
  { type: 'BOOSTER_BUNDLE', slug: 'booster-bundles', navKey: 'boosterBundle' },
  { type: 'BOOSTER_PACK', slug: 'booster-packs', navKey: 'boosterPack' },
  { type: 'SLEEVED_BOOSTER', slug: 'sleeved-boosters', navKey: 'sleevedBooster' },
  { type: 'BLISTER', slug: 'blisters', navKey: 'blister' },
  { type: 'TIN', slug: 'tins', navKey: 'tin' },
  { type: 'COLLECTION_BOX', slug: 'collectieboxen', navKey: 'collectionBox' },
  { type: 'STARTER_DECK', slug: 'starterdecks', navKey: 'starterDeck' },
  { type: 'SINGLE', slug: 'losse-kaarten', navKey: 'single' },
  { type: 'GRADED_SLAB', slug: 'graded-kaarten', navKey: 'gradedSlab' },
  { type: 'BULK', slug: 'bulk', navKey: 'bulk' },
  { type: 'MYSTERY_BOX', slug: 'mystery-boxen', navKey: 'mysteryBox' },
  { type: 'ACCESSORY', slug: 'accessoires', navKey: null },
]

export const ACCESSORY_TYPES = [
  { type: 'SLEEVES', slug: 'sleeves', navKey: 'sleeves' },
  { type: 'TOPLOADERS', slug: 'toploaders', navKey: 'toploaders' },
  { type: 'DECK_BOXES', slug: 'deckboxen', navKey: 'deckBoxes' },
  { type: 'PLAYMATS', slug: 'playmats', navKey: 'playmats' },
  { type: 'BINDERS', slug: 'verzamelmappen', navKey: 'binders' },
  { type: 'DISPLAY_CASES', slug: 'displaycases', navKey: 'displayCases' },
  { type: 'STORAGE_BOXES', slug: 'opbergboxen', navKey: 'storageBoxes' },
]

export const LANGUAGES = ['EN', 'JP', 'CN', 'DE', 'FR']

export const CONDITIONS = ['MINT', 'NEAR_MINT', 'EXCELLENT', 'GOOD', 'LIGHT_PLAYED', 'PLAYED', 'POOR']

export const GRADING_COMPANIES = ['PSA', 'BGS', 'CGC', 'SGC', 'ACE', 'TAG']

export const typeBySlug = (slug) => PRODUCT_TYPES.find((item) => item.slug === slug)
export const accessoryBySlug = (slug) => ACCESSORY_TYPES.find((item) => item.slug === slug)

/** "Booster box": one product. */
export const typeLabel = (type) => t(`shop.types.${type}`)

/** "Booster boxen": a page or a filter. */
export function typePluralLabel(type) {
  const navKey = PRODUCT_TYPES.find((item) => item.type === type)?.navKey
  return navKey ? t(`nav.products.${navKey}`) : t('nav.accessories')
}

export function accessoryLabel(type) {
  const navKey = ACCESSORY_TYPES.find((item) => item.type === type)?.navKey
  return navKey ? t(`nav.accessoryTypes.${navKey}`) : type
}

export const languageLabel = (code) => t(`shop.languages.${code}`)

export const conditionLabel = (condition) => t(`collection.conditions.${condition}`)

export const gameOfSlug = (slug) => GAMES.find((game) => game.slug === slug)

/** The shop page of a game, a type within it, or a series. */
export function shopPath(game, { type, series, setCode } = {}) {
  if (series) return setCode ? `/${game}/serie/${series}/${encodeURIComponent(setCode)}` : `/${game}/serie/${series}`
  if (type) return `/${game}/${PRODUCT_TYPES.find((item) => item.type === type).slug}`
  return `/${game}`
}

export const productLink = (product) => ({ name: 'product', params: { slug: product.slug } })

/** What the shop shows of a product's details in one line, such as "Booster box, Engels, Surging Sparks". */
export function productFacts(product) {
  const facts = [product.accessoryType ? accessoryLabel(product.accessoryType) : typeLabel(product.type)]
  if (product.language) facts.push(languageLabel(product.language))
  if (product.condition) facts.push(conditionLabel(product.condition))
  if (product.gradingCompany) facts.push(`${product.gradingCompany} ${product.grade}`)
  if (product.setName) facts.push(product.setName)
  return facts
}

/** Euros typed by the admin or in a price filter, as cents. "12,95" and "12.95" both work. Null when empty or not a number. */
export function eurosToCents(value) {
  const text = String(value ?? '').trim().replace(/\s|€/g, '')
  if (!text) return null
  const normalised = text.includes(',') ? text.replace(/\./g, '').replace(',', '.') : text
  const amount = Number(normalised)
  return Number.isFinite(amount) && amount >= 0 ? Math.round(amount * 100) : null
}

/** Cents as euros for an input field: "12,95". */
export function centsToEuros(cents) {
  return cents === null || cents === undefined ? '' : (cents / 100).toFixed(2).replace('.', ',')
}
