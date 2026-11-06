import { Anchor, BookOpen, CalendarClock, Flame, Layers, Sparkles, Zap } from 'lucide-vue-next'
import BallMark from '../components/BallMark.vue'

const link = (to, labelKey) => ({ to, labelKey })

const productLinks = (base, types) =>
  types.map(([slug, key]) => link(`${base}/${slug}`, `nav.products.${key}`))

const BOOSTER_BOX = ['booster-boxen', 'boosterBox']
const BOOSTER_PACK = ['booster-packs', 'boosterPack']
const COLLECTION_BOX = ['collectieboxen', 'collectionBox']
const STARTER_DECK = ['starterdecks', 'starterDeck']
const SINGLE = ['losse-kaarten', 'single']
const GRADED_SLAB = ['graded-kaarten', 'gradedSlab']
const BULK = ['bulk', 'bulk']
const MYSTERY_BOX = ['mystery-boxen', 'mysteryBox']

const collectionFeature = {
  to: '/collectie',
  titleKey: 'nav.feature.collection.title',
  textKey: 'nav.feature.collection.text',
  actionKey: 'nav.feature.collection.action',
}

const catalogLink = (slug) => link(`/kaarten/${slug}`, 'nav.allSets')

const gameColumns = (slug) => [
  {
    titleKey: 'nav.groups.products',
    links: productLinks(`/${slug}`, [BOOSTER_BOX, BOOSTER_PACK, COLLECTION_BOX, STARTER_DECK]),
  },
  {
    titleKey: 'nav.groups.singles',
    links: [catalogLink(slug), ...productLinks(`/${slug}`, [SINGLE, GRADED_SLAB, BULK, MYSTERY_BOX])],
  },
]

/** The main navigation. Items with `columns` open a dropdown, the others are plain links. */
export const mainNav = [
  {
    key: 'pokemon',
    to: '/pokemon',
    labelKey: 'nav.pokemon',
    game: 'pokemon',
    icon: BallMark,
    columns: [
      {
        titleKey: 'nav.groups.products',
        links: productLinks('/pokemon', [
          BOOSTER_BOX,
          ['elite-trainer-boxen', 'eliteTrainerBox'],
          ['booster-bundles', 'boosterBundle'],
          BOOSTER_PACK,
          ['sleeved-boosters', 'sleevedBooster'],
          ['blisters', 'blister'],
          ['tins', 'tin'],
          COLLECTION_BOX,
          STARTER_DECK,
        ]),
      },
      {
        titleKey: 'nav.groups.series',
        links: [
          link('/pokemon/serie/mega-evolution', 'nav.series.megaEvolution'),
          link('/pokemon/serie/scarlet-violet', 'nav.series.scarletViolet'),
          link('/pokemon/serie/sword-shield', 'nav.series.swordShield'),
          link('/pokemon/serie/sun-moon', 'nav.series.sunMoon'),
          link('/pokemon/serie/xy', 'nav.series.xy'),
        ],
      },
      {
        titleKey: 'nav.groups.singles',
        links: [catalogLink('pokemon'), ...productLinks('/pokemon', [SINGLE, GRADED_SLAB, BULK, MYSTERY_BOX])],
      },
    ],
    feature: {
      to: '/collectie/pokedex',
      titleKey: 'nav.feature.pokedex.title',
      textKey: 'nav.feature.pokedex.text',
      actionKey: 'nav.feature.pokedex.action',
    },
  },
  {
    key: 'lorcana',
    to: '/lorcana',
    labelKey: 'nav.lorcana',
    game: 'lorcana',
    icon: Sparkles,
    columns: gameColumns('lorcana'),
    feature: collectionFeature,
  },
  {
    key: 'magic',
    to: '/magic',
    labelKey: 'nav.magic',
    game: 'mtg',
    icon: Flame,
    columns: gameColumns('magic'),
    feature: collectionFeature,
  },
  {
    key: 'one-piece',
    to: '/one-piece',
    labelKey: 'nav.onePiece',
    game: 'one-piece',
    icon: Anchor,
    columns: gameColumns('one-piece'),
    feature: collectionFeature,
  },
  {
    key: 'accessories',
    to: '/accessoires',
    labelKey: 'nav.accessories',
    icon: Layers,
    columns: [
      {
        titleKey: 'nav.groups.protect',
        links: [
          link('/accessoires/sleeves', 'nav.accessoryTypes.sleeves'),
          link('/accessoires/toploaders', 'nav.accessoryTypes.toploaders'),
          link('/accessoires/deckboxen', 'nav.accessoryTypes.deckBoxes'),
          link('/accessoires/playmats', 'nav.accessoryTypes.playmats'),
        ],
      },
      {
        titleKey: 'nav.groups.store',
        links: [
          link('/accessoires/verzamelmappen', 'nav.accessoryTypes.binders'),
          link('/accessoires/displaycases', 'nav.accessoryTypes.displayCases'),
          link('/accessoires/opbergboxen', 'nav.accessoryTypes.storageBoxes'),
        ],
      },
    ],
    feature: collectionFeature,
  },
  { key: 'pre-order', to: '/pre-order', labelKey: 'nav.preOrder', icon: CalendarClock },
  { key: 'drops', to: '/drops', labelKey: 'nav.drops', icon: Zap },
  {
    key: 'collection',
    to: '/collectie',
    labelKey: 'nav.collection',
    icon: BookOpen,
    columns: [
      {
        titleKey: 'nav.groups.myCollection',
        links: [
          link('/collectie', 'nav.collectionLinks.overview'),
          link('/collectie/pokedex', 'nav.collectionLinks.pokedex'),
          link('/verlanglijst', 'nav.collectionLinks.wishlist'),
        ],
      },
      {
        titleKey: 'nav.groups.catalog',
        links: [
          link('/kaarten/pokemon', 'nav.pokemon'),
          link('/kaarten/lorcana', 'nav.lorcana'),
          link('/kaarten/magic', 'nav.magic'),
          link('/kaarten/one-piece', 'nav.onePiece'),
        ],
      },
    ],
    feature: {
      to: '/collectie/pokedex',
      titleKey: 'nav.feature.pokedex.title',
      textKey: 'nav.feature.pokedex.text',
      actionKey: 'nav.feature.pokedex.action',
    },
  },
]

/** Every destination in the menu that has no real page yet, once. The router shows a placeholder for these. */
export function navDestinations() {
  const destinations = new Map()
  const add = (to, labelKey, game) => {
    if (!to.startsWith('/kaarten/') && !destinations.has(to)) destinations.set(to, { to, labelKey, game })
  }

  for (const item of mainNav) {
    add(item.to, item.labelKey, item.game)
    for (const column of item.columns ?? []) {
      column.links.forEach(({ to, labelKey }) => add(to, labelKey, item.game))
    }
    if (item.feature) add(item.feature.to, item.feature.titleKey, item.game)
  }
  return [...destinations.values()]
}

export const footerInfoLinks = [
  { to: '/info/verzending', labelKey: 'footer.shipping' },
  { to: '/info/retourneren', labelKey: 'footer.returns' },
  { to: '/info/veelgestelde-vragen', labelKey: 'footer.faq' },
  { to: '/info/contact', labelKey: 'footer.contact' },
]

export const footerShopLinks = [
  { to: '/info/over-ons', labelKey: 'footer.about' },
  { to: '/info/algemene-voorwaarden', labelKey: 'footer.terms' },
  { to: '/info/privacybeleid', labelKey: 'footer.privacy' },
]
