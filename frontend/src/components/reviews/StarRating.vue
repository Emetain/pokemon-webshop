<script setup>
import { Star } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

/** 1 to 5 stars. An average shows as whole stars: the page writes the exact number next to it. */
const props = defineProps({
  rating: { type: Number, required: true },
  size: { type: Number, default: 16 },
})
</script>

<template>
  <span class="stars" role="img" :aria-label="t('reviews.stars', { rating: String(rating).replace('.', ',') })">
    <Star
      v-for="index in 5"
      :key="index"
      class="star"
      :class="{ full: Math.round(props.rating) >= index }"
      :size="size"
      aria-hidden="true"
    />
  </span>
</template>

<style scoped>
.stars {
  display: inline-flex;
  gap: 1px;
  vertical-align: middle;
}

.star {
  color: var(--color-line-strong);
}

.star.full {
  color: var(--color-warning);
  fill: currentColor;
}
</style>
