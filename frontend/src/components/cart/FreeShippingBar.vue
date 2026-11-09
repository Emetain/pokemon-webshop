<script setup>
import { computed } from 'vue'
import BallMark from '../BallMark.vue'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** How far the cart is from free shipping. A Poké Ball rolls along the track towards it. */
const props = defineProps({
  shipping: { type: Object, default: null },
  subtotalCents: { type: Number, required: true },
})

const threshold = computed(() => props.shipping?.freeFromCents ?? null)
const reached = computed(() => threshold.value !== null && props.shipping.remainingForFreeCents === null)
const progress = computed(() => {
  if (threshold.value === null) return 0
  if (reached.value) return 100
  return Math.min(100, Math.round((props.subtotalCents / threshold.value) * 100))
})
</script>

<template>
  <div v-if="threshold !== null" class="free-shipping" :class="{ reached }">
    <p class="text" aria-live="polite">
      <template v-if="reached">{{ t('cart.freeShippingReached') }}</template>
      <template v-else>{{ t('cart.freeShippingLeft', { amount: formatMoney(shipping.remainingForFreeCents, 'EUR') }) }}</template>
    </p>
    <div
      class="track"
      role="progressbar"
      :aria-label="t('cart.freeShippingLabel')"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="progress"
    >
      <span class="fill" :style="{ width: `${progress}%` }"></span>
      <span class="ball" :style="{ left: `${progress}%`, '--turn': `${progress * 3.6}deg` }">
        <BallMark />
      </span>
    </div>
  </div>
</template>

<style scoped>
.free-shipping {
  display: grid;
  gap: var(--space-2);
}

.text {
  font-size: var(--text-sm);
  font-weight: 600;
}

.reached .text {
  color: var(--color-success);
}

/* Room on both ends, so the ball never sticks out of the track. */
.track {
  position: relative;
  height: 0.5rem;
  margin: var(--space-1) 0.625rem;
  border-radius: var(--radius-round);
  background: var(--color-tint-strong);
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 0.5s ease-out;
}

.reached .fill {
  background: var(--color-success);
}

.ball {
  position: absolute;
  top: 50%;
  width: 1.25rem;
  height: 1.25rem;
  transform: translate(-50%, -50%) rotate(var(--turn));
  transition: left 0.5s ease-out, transform 0.5s ease-out;
}

@media (prefers-reduced-motion: reduce) {
  .fill,
  .ball {
    transition: none;
  }
}
</style>
