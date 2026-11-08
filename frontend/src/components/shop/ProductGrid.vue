<script setup>
import ProductTile from './ProductTile.vue'
import { t } from '../../utils/i18n'

const SKELETON_TILES = 8

defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** Fewer columns, for a page with a sidebar. */
  narrow: { type: Boolean, default: false },
})

const emit = defineEmits(['wished', 'error'])
</script>

<template>
  <ul v-if="loading" class="grid" :class="{ narrow }" :aria-label="t('shop.loading')" aria-busy="true">
    <li v-for="tile in SKELETON_TILES" :key="tile" class="skeleton"></li>
  </ul>
  <ul v-else class="grid" :class="{ narrow }">
    <li v-for="(product, index) in products" :key="product.id">
      <ProductTile
        :product="product"
        :eager="index < 4"
        @wished="emit('wished', product, $event)"
        @error="emit('error', $event)"
      >
      </ProductTile>
      <slot name="actions" :product="product" />
    </li>
  </ul>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6) var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.grid > li {
  display: flex;
  flex-direction: column;
}

.skeleton {
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

@media (min-width: 640px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    column-gap: var(--space-4);
  }
}

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .grid.narrow {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .grid.narrow {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
