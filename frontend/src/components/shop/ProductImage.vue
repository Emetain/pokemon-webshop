<script setup>
import { computed, ref, watch } from 'vue'
import { Package } from 'lucide-vue-next'
import CardImage from '../catalog/CardImage.vue'
import { typeLabel } from '../../data/shop'

/**
 * The picture of a product in a square. A card from the catalog goes through CardImage, so nothing ever covers or
 * changes it. Without a picture, the set logo or the kind of product fills the square.
 */
const props = defineProps({
  src: { type: String, default: null },
  isCard: { type: Boolean, default: false },
  alt: { type: String, default: '' },
  setLogo: { type: String, default: null },
  type: { type: String, default: '' },
  game: { type: String, default: null },
  eager: { type: Boolean, default: false },
})

const failed = ref(false)
watch(() => props.src, () => (failed.value = false))

const showPhoto = computed(() => props.src && !props.isCard && !failed.value)
</script>

<template>
  <div class="product-image" :data-game="game ?? undefined">
    <CardImage v-if="isCard && src" class="card" :src="src" :alt="alt" :eager="eager" />
    <img
      v-else-if="showPhoto"
      class="photo"
      :src="src"
      :alt="alt"
      :loading="eager ? 'eager' : 'lazy'"
      decoding="async"
      @error="failed = true"
    />
    <div v-else class="placeholder">
      <img v-if="setLogo" class="logo" :src="setLogo" alt="" loading="lazy" decoding="async" />
      <Package v-else class="icon" :size="34" aria-hidden="true" />
      <span v-if="type" class="type">{{ typeLabel(type) }}</span>
      <span v-if="alt" class="visually-hidden">{{ alt }}</span>
    </div>
  </div>
</template>

<style scoped>
.product-image {
  container-type: inline-size;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  overflow: hidden;
  padding: min(var(--space-3), 8%);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.photo {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

/* The card keeps its own shape inside the square, at full height. */
.card {
  height: 100%;
  max-width: 100%;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
  height: 100%;
  border: 1.5px dashed var(--color-line-strong);
  border-radius: var(--radius-sm);
  color: var(--accent);
  text-align: center;
}

.logo {
  width: 70%;
  max-height: 45%;
  object-fit: contain;
}

.type {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 600;
}

/* A thumbnail, as in the cart and order lists: the kind of product doesn't fit, the icon or logo says enough. */
@container (max-width: 7rem) {
  .type {
    display: none;
  }

  .icon {
    width: 55%;
    height: auto;
  }
}
</style>
