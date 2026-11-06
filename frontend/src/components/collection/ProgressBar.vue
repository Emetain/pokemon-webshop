<script setup>
import { computed } from 'vue'
import { formatNumber, formatPercent } from '../../utils/format'
import { t } from '../../utils/i18n'

/** `progress` is `{ owned, total, percent }`. The bar takes its colour from the game around it. */
const props = defineProps({
  progress: { type: Object, required: true },
  /** What the bar measures, for screen readers. */
  name: { type: String, required: true },
  compact: { type: Boolean, default: false },
})

// One card of 110,000 still has to look like a start, and rounds down to 0%.
const startedButTiny = computed(() => props.progress.owned > 0 && props.progress.percent < 0.1)

const percentText = computed(() => (startedButTiny.value ? '<0,1%' : formatPercent(props.progress.percent)))

const width = computed(() => (props.progress.owned > 0 ? `max(${props.progress.percent}%, 3px)` : '0'))
</script>

<template>
  <div class="progress" :class="{ compact }">
    <div
      class="track"
      role="progressbar"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="progress.percent"
      :aria-label="t('collection.progressLabel', { name })"
    >
      <div class="fill" :style="{ width }"></div>
    </div>
    <p class="numbers">
      <span>
        {{ t('collection.progressOf', { owned: formatNumber(progress.owned), total: formatNumber(progress.total) }) }}
      </span>
      <span class="percent">{{ percentText }}</span>
    </p>
  </div>
</template>

<style scoped>
.track {
  height: 0.75rem;
  overflow: hidden;
  border-radius: var(--radius-round);
  background: var(--color-tint-strong);
}

.fill {
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 240ms ease-out;
}

.numbers {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.percent {
  color: var(--color-ink);
  font-weight: 700;
}

.compact .track {
  height: 0.375rem;
}

.compact .numbers {
  font-size: var(--text-xs);
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    transition: none;
  }
}
</style>
