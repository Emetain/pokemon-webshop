<script setup>
import { computed, ref } from 'vue'
import { cardCountLabel } from '../../utils/catalog'
import { formatDate } from '../../utils/format'

const props = defineProps({
  set: { type: Object, required: true },
})

const failed = ref(false)
const image = computed(() => (failed.value ? null : props.set.logoUrl ?? props.set.symbolUrl))
const isSymbol = computed(() => !props.set.logoUrl)
</script>

<template>
  <router-link class="tile" :to="{ name: 'set', params: { game: set.game, setCode: set.code } }">
    <span class="art">
      <img
        v-if="image"
        class="logo"
        :class="{ symbol: isSymbol }"
        :src="image"
        alt=""
        loading="lazy"
        decoding="async"
        @error="failed = true"
      />
      <!-- Many sets have no artwork at all. Their code is what collectors know them by. -->
      <span v-else class="code">{{ set.code }}</span>
    </span>
    <span class="name">{{ set.name }}</span>
    <span class="details">
      <span v-if="set.releaseDate">{{ formatDate(set.releaseDate) }}</span>
      <span>{{ cardCountLabel(set.cardCount) }}</span>
    </span>
  </router-link>
</template>

<style scoped>
.tile {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-ink);
  text-decoration: none;
  transition: border-color 0.2s;
}

.tile:hover {
  border-color: var(--accent);
}

.art {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-sm);
}

.logo {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo.symbol {
  width: 50%;
  height: 62%;
}

.code {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: var(--space-2);
  background: color-mix(in srgb, var(--accent) 9%, var(--color-surface));
  color: var(--accent);
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 1;
  overflow-wrap: anywhere;
  text-align: center;
}

.name {
  margin-top: var(--space-3);
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
}

.tile:hover .name {
  color: var(--accent);
}

.details {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-3);
  margin-top: auto;
  padding-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

@media (min-width: 768px) {
  .code {
    font-size: var(--text-2xl);
  }
}
</style>
