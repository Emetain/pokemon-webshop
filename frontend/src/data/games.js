/** The games of the catalog. `slug` is the name in URLs and the API, `accent` the value for `data-game`. */
export const GAMES = [
  { slug: 'pokemon', labelKey: 'nav.pokemon', accent: 'pokemon' },
  { slug: 'lorcana', labelKey: 'nav.lorcana', accent: 'lorcana' },
  { slug: 'magic', labelKey: 'nav.magic', accent: 'mtg' },
  { slug: 'one-piece', labelKey: 'nav.onePiece', accent: 'one-piece' },
]

export const findGame = (slug) => GAMES.find((game) => game.slug === slug)
