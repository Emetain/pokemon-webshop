<script setup>
import { useId } from 'vue'
import ProductTile from './ProductTile.vue'
import { t } from '../../utils/i18n'

/** A row of products on the home page that scrolls sideways, with a link to the full list. */
defineProps({
  title: { type: String, required: true },
  products: { type: Array, required: true },
  to: { type: [String, Object], default: null },
  /** Skeleton tiles while the products load (2026-11-21). */
  loading: { type: Boolean, default: false },
  /** Room around the tiles for the glowing frames of wanted cards (2026-11-23), which the scrolling list would cut off. */
  framed: { type: Boolean, default: false },
})

const SKELETON_TILES = 6

const emit = defineEmits(['wished', 'error'])
const id = useId()
</script>

<template>
  <section class="product-row" :aria-labelledby="id">
    <div class="head">
      <h2 :id="id">{{ title }}</h2>
      <router-link v-if="to" class="all" :to="to">{{ t('home.viewAll') }}</router-link>
    </div>
    <!-- The list itself scrolls, so the page never does. It can take focus for scrolling with the keyboard. -->
    <ul v-if="loading" class="scroller" aria-busy="true" :aria-label="t('shop.loading')">
      <li v-for="tile in SKELETON_TILES" :key="tile" class="item skeleton-tile" aria-hidden="true">
        <span class="skeleton picture"></span>
        <span class="skeleton line"></span>
        <span class="skeleton line short"></span>
      </li>
    </ul>
    <ul v-else class="scroller" :class="{ framed }" tabindex="0" :aria-labelledby="id">
      <li v-for="product in products" :key="product.id" class="item">
        <ProductTile :product="product" @wished="emit('wished', product, $event)" @error="emit('error', $event)" />
      </li>
    </ul>
  </section>
</template>

<style scoped>
/* A grid or flex parent would otherwise grow to the full width of the row. */
.product-row {
  min-width: 0;
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
}

.all {
  flex: none;
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
}

@media (hover: hover) {
  .all:hover {
    text-decoration: underline;
  }
}

/* Positioned, so hidden labels inside the tiles scroll with the row instead of widening the page. */
.scroller {
  position: relative;
  display: grid;
  grid-auto-columns: calc((100% - var(--space-3)) / 2.25);
  grid-auto-flow: column;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0 0 var(--space-3);
  overflow-x: auto;
  list-style: none;
  overscroll-behavior-x: contain;
  scroll-snap-type: x mandatory;
  scrollbar-width: thin;
}

.item {
  scroll-snap-align: start;
}

/* The glow reaches past the tile. The list makes room for it on all sides and moves out by as much at the sides, so
   the tiles still line up with the rest of the page. Less than the page gutter, so the page never scrolls sideways. */
.scroller.framed {
  --glow-room: var(--space-3);
  margin-inline: calc(-1 * var(--glow-room));
  padding: var(--glow-room) var(--glow-room) calc(var(--glow-room) + var(--space-1));
  scroll-padding-inline: var(--glow-room);
}

.skeleton-tile {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.skeleton.picture {
  aspect-ratio: 1;
  border-radius: var(--radius-md);
}

.skeleton.line {
  width: 85%;
  height: 0.875rem;
}

.skeleton.line.short {
  width: 35%;
}

@media (min-width: 640px) {
  .scroller {
    grid-auto-columns: calc((100% - 3 * var(--space-4)) / 3.5);
    gap: var(--space-4);
  }
}

@media (min-width: 1024px) {
  .scroller {
    grid-auto-columns: calc((100% - 4 * var(--space-4)) / 5);
  }
}

@media (min-width: 1280px) {
  .scroller {
    grid-auto-columns: calc((100% - 5 * var(--space-4)) / 6);
  }
}
</style>
