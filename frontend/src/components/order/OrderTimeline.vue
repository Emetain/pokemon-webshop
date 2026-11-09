<script setup>
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The road of an order: the steps it took, then the ones still to come. A cancelled or refunded order ends where it
 * stopped.
 */
const props = defineProps({
  timeline: { type: Array, required: true },
  status: { type: String, required: true },
})

const ROAD = ['PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED']

const steps = computed(() => {
  const done = props.timeline.map((step) => ({ ...step, done: true }))
  if (['CANCELLED', 'REFUNDED'].includes(props.status)) return done
  const reached = new Set(done.map((step) => step.status))
  const lastIndex = Math.max(...done.map((step) => ROAD.indexOf(step.status)), 0)
  const upcoming = ROAD.slice(lastIndex + 1)
    .filter((status) => !reached.has(status) && !(status === 'PROCESSING' && props.status === 'SHIPPED'))
    .map((status) => ({ status, done: false }))
  return [...done, ...upcoming]
})
</script>

<template>
  <ol class="timeline">
    <li v-for="(step, index) in steps" :key="`${step.status}-${index}`" class="step" :class="{ done: step.done, stop: ['CANCELLED', 'REFUNDED'].includes(step.status) }">
      <span class="dot" aria-hidden="true">
        <Check v-if="step.done" :size="14" />
      </span>
      <span class="label">{{ t(`orders.steps.${step.status}`) }}</span>
      <time v-if="step.at" class="when" :datetime="step.at">{{ formatDateTime(step.at) }}</time>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.step {
  position: relative;
  display: grid;
  grid-template-columns: 1.5rem minmax(0, 1fr);
  column-gap: var(--space-3);
  padding-bottom: var(--space-4);
}

.step:last-child {
  padding-bottom: 0;
}

/* The line between two steps, solid where the order has been. */
.step:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 1.5rem;
  bottom: 0;
  left: calc(0.75rem - 1px);
  width: 2px;
  background: var(--color-line);
}

.step.done:not(:last-child)::before {
  background: var(--color-brand);
}

.dot {
  display: flex;
  grid-row: span 2;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 2px solid var(--color-line-strong);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-on-brand);
}

.done .dot {
  border-color: var(--color-brand);
  background: var(--color-brand);
}

.stop .dot {
  border-color: var(--color-ink-soft);
  background: var(--color-ink-soft);
}

.label {
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.5rem;
}

.step:not(.done) .label {
  color: var(--color-ink-soft);
  font-weight: 500;
}

.when {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
