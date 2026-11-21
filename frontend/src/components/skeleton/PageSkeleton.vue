<script setup>
import { t } from '../../utils/i18n'

/**
 * The shape of a page while it loads (2026-11-21, the owner's wish: a click should never look like nothing happened).
 * Shown by a view while it waits for its data, and by the layout while the next page itself is still on its way
 * (`meta.skeleton` on the route, see `stores/navigation.js`). The shapes follow the real pages roughly: grey blocks
 * where pictures, titles, prices and rows will be. Screen readers hear "Laden…" once; the blocks are hidden from them.
 *
 * Kinds: `cards` (a card list), `sets`, `products` (a shop list), `card` (a card page), `product`, `collection`,
 * `list` (orders, alerts, tickets, news), `article` (info pages, a news article), `form` (login, account, checkout),
 * `home` and `table` (admin).
 */
defineProps({
  kind: { type: String, default: 'article' },
  /** The coloured band with the breadcrumb and the title that catalog and collection pages start with. */
  band: { type: Boolean, default: false },
  /** In its own container with page padding; false inside a page that already has one. */
  page: { type: Boolean, default: true },
  /** The title lines of a list, form, table or article; false when the page already shows its title. */
  heading: { type: Boolean, default: true },
})

const CARD_TILES = 12
const PRODUCT_TILES = 10
const SET_TILES = 10
const ROWS = 6
// Text lines of different lengths, so a block of text looks like text.
const LINES = [100, 96, 88, 100, 72, 94, 100, 64]

const width = (percent) => ({ width: `${percent}%` })
</script>

<template>
  <div class="page-skeleton" aria-busy="true">
    <p class="visually-hidden" role="status">{{ t('catalog.loading') }}</p>

    <div v-if="band || kind === 'home'" class="band" :class="{ hero: kind === 'home' }" aria-hidden="true">
      <div class="container band-body">
        <template v-if="kind === 'home'">
          <span class="bar on-brand" :style="width(60)"></span>
          <span class="bar on-brand big" :style="width(46)"></span>
          <span class="bar on-brand" :style="width(70)"></span>
          <span class="bar on-brand button"></span>
        </template>
        <template v-else>
          <span class="bar on-brand small" :style="width(22)"></span>
          <span class="bar on-brand title" :style="width(38)"></span>
        </template>
      </div>
    </div>

    <div :class="{ container: page, body: page }" aria-hidden="true">
      <!-- A card list: filters, then card-shaped tiles with a name and a price under each. -->
      <template v-if="kind === 'cards'">
        <div class="filters">
          <span class="skeleton pill"></span>
          <span class="skeleton pill"></span>
          <span class="skeleton pill wide"></span>
        </div>
        <ul class="card-grid">
          <li v-for="tile in CARD_TILES" :key="tile" class="tile">
            <span class="skeleton card-shape"></span>
            <span class="skeleton line" :style="width(80)"></span>
            <span class="skeleton line short" :style="width(40)"></span>
          </li>
        </ul>
      </template>

      <ul v-else-if="kind === 'sets'" class="set-grid">
        <li v-for="tile in SET_TILES" :key="tile" class="skeleton set"></li>
      </ul>

      <ul v-else-if="kind === 'products'" class="product-grid">
        <li v-for="tile in PRODUCT_TILES" :key="tile" class="tile">
          <span class="skeleton square"></span>
          <span class="skeleton line" :style="width(90)"></span>
          <span class="skeleton line" :style="width(60)"></span>
          <span class="skeleton line price"></span>
        </li>
      </ul>

      <!-- A card page: the card on the left, its facts and prices on the right. -->
      <div v-else-if="kind === 'card'" class="split">
        <span class="skeleton card-shape big"></span>
        <div class="stack">
          <span class="skeleton line heading" :style="width(55)"></span>
          <div v-for="row in ROWS" :key="row" class="fact">
            <span class="skeleton line" :style="width(30)"></span>
            <span class="skeleton line" :style="width(45)"></span>
          </div>
          <span class="skeleton block"></span>
        </div>
      </div>

      <div v-else-if="kind === 'product'" class="split">
        <span class="skeleton square big"></span>
        <div class="stack">
          <span class="skeleton line small" :style="width(30)"></span>
          <span class="skeleton line heading" :style="width(80)"></span>
          <span class="skeleton line heading" :style="width(35)"></span>
          <span class="skeleton button"></span>
          <span v-for="(line, index) in LINES.slice(0, 5)" :key="index" class="skeleton line" :style="width(line)"></span>
        </div>
      </div>

      <!-- The collection: the value, its graph, then rows. -->
      <div v-else-if="kind === 'collection'" class="stack">
        <span class="skeleton line small" :style="width(12)"></span>
        <span class="skeleton line value" :style="width(34)"></span>
        <span class="skeleton chart"></span>
        <div class="pills">
          <span v-for="pill in 7" :key="pill" class="skeleton pill round"></span>
        </div>
        <div v-for="row in 4" :key="row" class="row">
          <span class="skeleton line" :style="width(50)"></span>
          <span class="skeleton line bar-line"></span>
        </div>
      </div>

      <div v-else-if="kind === 'list'" class="stack narrow">
        <template v-if="heading">
          <span class="skeleton line heading" :style="width(40)"></span>
          <span class="skeleton line" :style="width(70)"></span>
        </template>
        <div v-for="row in ROWS" :key="row" class="list-row">
          <span class="skeleton thumb"></span>
          <div class="stack tight">
            <span class="skeleton line" :style="width(60)"></span>
            <span class="skeleton line small" :style="width(35)"></span>
          </div>
          <span class="skeleton line end"></span>
        </div>
      </div>

      <div v-else-if="kind === 'form'" class="stack form">
        <span v-if="heading" class="skeleton line heading" :style="width(50)"></span>
        <div v-for="field in 4" :key="field" class="stack tight">
          <span class="skeleton line small" :style="width(25)"></span>
          <span class="skeleton field"></span>
        </div>
        <span class="skeleton button"></span>
      </div>

      <div v-else-if="kind === 'home'" class="stack">
        <span class="skeleton line heading" :style="width(25)"></span>
        <ul class="product-grid">
          <li v-for="tile in 5" :key="tile" class="tile">
            <span class="skeleton square"></span>
            <span class="skeleton line" :style="width(85)"></span>
            <span class="skeleton line price"></span>
          </li>
        </ul>
      </div>

      <div v-else-if="kind === 'table'" class="stack">
        <span v-if="heading" class="skeleton line heading" :style="width(30)"></span>
        <div class="filters">
          <span class="skeleton pill wide"></span>
          <span class="skeleton pill"></span>
        </div>
        <span class="skeleton table-head"></span>
        <div v-for="row in 8" :key="row" class="table-row">
          <span class="skeleton line" :style="width(30)"></span>
          <span class="skeleton line" :style="width(18)"></span>
          <span class="skeleton line" :style="width(14)"></span>
        </div>
      </div>

      <!-- An article: a title and paragraphs. -->
      <div v-else class="stack narrow">
        <span v-if="heading" class="skeleton line heading" :style="width(60)"></span>
        <span v-for="(line, index) in LINES" :key="index" class="skeleton line" :style="width(line)"></span>
        <span class="gap"></span>
        <span v-for="(line, index) in LINES.slice(2, 7)" :key="`b${index}`" class="skeleton line" :style="width(line)"></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-skeleton {
  width: 100%;
}

.body {
  padding-block: var(--space-5) var(--space-7);
}

/* The band of catalog and collection pages, in the page's own accent, with light bars for the breadcrumb and title. */
.band {
  background: var(--accent);
}

.band-body {
  display: grid;
  gap: var(--space-3);
  padding-block: var(--space-4) var(--space-5);
}

.band.hero .band-body {
  gap: var(--space-4);
  padding-block: var(--space-7);
}

.bar {
  display: block;
  height: 1rem;
  border-radius: var(--radius-sm);
}

.bar.on-brand {
  background: var(--color-skeleton-on-brand);
}

.bar.small {
  height: 0.75rem;
}

.bar.title {
  height: 2rem;
}

.bar.big {
  height: 3.25rem;
}

.bar.button {
  width: 11rem;
  height: var(--control-height);
}

.line {
  display: block;
  height: 0.875rem;
}

.line.small {
  height: 0.7rem;
}

.line.heading {
  height: 1.75rem;
}

.line.value {
  height: 3.5rem;
}

.line.price {
  width: 35%;
  height: 1rem;
}

.stack {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
}

.stack.tight {
  gap: var(--space-1);
}

.stack.narrow {
  max-width: 48rem;
}

.stack.form {
  max-width: 32rem;
}

.gap {
  height: var(--space-2);
}

.filters,
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.pills {
  margin-bottom: 0;
}

.pill {
  width: 7rem;
  height: var(--touch-target);
  border-radius: var(--radius-sm);
}

.pill.wide {
  width: 12rem;
}

.pill.round {
  width: 2.75rem;
  height: 2.25rem;
  border-radius: var(--radius-round);
}

.tile {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
}

.card-shape {
  aspect-ratio: 63 / 88;
  border-radius: var(--radius-card);
}

.square {
  aspect-ratio: 1;
  border-radius: var(--radius-md);
}

/* The same columns as CardGrid.vue and ProductGrid.vue, so nothing moves when the real tiles arrive. */
.card-grid,
.product-grid,
.set-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5) var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.set-grid {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 14rem), 1fr));
}

.set {
  height: 7.5rem;
  border-radius: var(--radius-md);
}

.split {
  display: grid;
  gap: var(--space-5);
}

.card-shape.big,
.square.big {
  width: 100%;
  max-width: 24rem;
  margin-inline: auto;
}

.fact {
  display: grid;
  grid-template-columns: 8rem 1fr;
  gap: var(--space-2);
  padding-block: var(--space-1);
}

.block {
  height: 8rem;
  margin-top: var(--space-3);
  border-radius: var(--radius-md);
}

.button {
  width: 12rem;
  height: var(--control-height);
  border-radius: var(--radius-sm);
}

.chart {
  height: 13rem;
  border-radius: var(--radius-md);
}

.row {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.bar-line {
  height: 0.6rem;
  border-radius: var(--radius-round);
}

.list-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-2);
}

.list-row .stack {
  flex: 1;
}

.thumb {
  flex: none;
  width: 3rem;
  height: 4.2rem;
  border-radius: var(--radius-xs);
}

.line.end {
  flex: none;
  width: 4.5rem;
}

.field {
  height: var(--control-height);
  border-radius: var(--radius-sm);
}

.table-head {
  height: 2.5rem;
  border-radius: var(--radius-xs);
}

.table-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: var(--space-4);
  padding-block: var(--space-2);
}

@media (min-width: 560px) {
  .card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 640px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    column-gap: var(--space-4);
  }
}

@media (min-width: 768px) {
  .card-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    column-gap: var(--space-4);
  }
}

@media (min-width: 900px) {
  .split {
    grid-template-columns: minmax(0, 26rem) minmax(0, 1fr);
    gap: var(--space-7);
  }

  .card-shape.big,
  .square.big {
    max-width: none;
  }
}

@media (min-width: 1024px) {
  .card-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .product-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .card-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .product-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
</style>
