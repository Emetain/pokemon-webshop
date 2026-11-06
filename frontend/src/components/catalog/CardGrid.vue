<script setup>
import CardTile from './CardTile.vue'
import { t } from '../../utils/i18n'

const SKELETON_TILES = 12

defineProps({
  cards: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  showSet: { type: Boolean, default: false },
})
</script>

<template>
  <ul v-if="loading" class="grid" :aria-label="t('catalog.loading')" aria-busy="true">
    <li v-for="tile in SKELETON_TILES" :key="tile" class="skeleton"></li>
  </ul>
  <ul v-else class="grid">
    <li v-for="card in cards" :key="card.id">
      <CardTile :card="card" :show-set="showSet" />
    </li>
  </ul>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5) var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.skeleton {
  aspect-ratio: 63 / 88;
  border-radius: var(--radius-card);
  background: var(--color-tint);
}

@media (min-width: 560px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 768px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    column-gap: var(--space-4);
  }
}

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
