<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BallMark from '../BallMark.vue'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * How far the cart is from free shipping. A Poké Ball rolls along the track towards it: it turns as far as a real ball
 * would over that distance, so it rolls instead of sliding.
 */
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

const track = ref(null)
const trackWidth = ref(0)
const ballSize = ref(0)
// Only once the track has its size does the ball roll: opening the cart must not set it rolling from the start.
const ready = ref(false)

const distance = computed(() => (trackWidth.value * progress.value) / 100)
const turn = computed(() => (ballSize.value ? (distance.value / (Math.PI * ballSize.value)) * 360 : 0))

const observer = new ResizeObserver(() => {
  // A closed drawer measures 0. The ball keeps its place for when the drawer opens again.
  if (!track.value?.clientWidth) return
  trackWidth.value = track.value.clientWidth
  ballSize.value = track.value.querySelector('.ball').offsetWidth
  if (!ready.value) requestAnimationFrame(() => (ready.value = true))
})

// The track is only there with a free shipping threshold, which can come later.
watch(track, (element) => {
  observer.disconnect()
  if (element) observer.observe(element)
})

onBeforeUnmount(() => observer.disconnect())
</script>

<template>
  <div v-if="threshold !== null" class="free-shipping" :class="{ reached }">
    <p class="text" aria-live="polite">
      <template v-if="reached">{{ t('cart.freeShippingReached') }}</template>
      <template v-else>{{ t('cart.freeShippingLeft', { amount: formatMoney(shipping.remainingForFreeCents, 'EUR') }) }}</template>
    </p>
    <div
      ref="track"
      class="track"
      :class="{ ready }"
      role="progressbar"
      :aria-label="t('cart.freeShippingLabel')"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="progress"
    >
      <span class="fill" :style="{ width: `${progress}%` }"></span>
      <span class="ball" :style="{ '--distance': `${distance}px`, '--turn': `${turn}deg` }">
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
}

.reached .fill {
  background: var(--color-success);
}

.ball {
  position: absolute;
  top: 50%;
  left: 0;
  width: 1.25rem;
  height: 1.25rem;
  transform: translate(calc(var(--distance) - 50%), -50%) rotate(var(--turn));
}

.ready .fill {
  transition: width 0.6s var(--ease-spring);
}

.ready .ball {
  transition: transform 0.6s var(--ease-spring);
}

@media (prefers-reduced-motion: reduce) {
  .ready .fill,
  .ready .ball {
    transition: none;
  }
}
</style>
