<script setup>
import { computed } from 'vue'
import { dateParts } from '../../utils/format'
import { t } from '../../utils/i18n'

/** A release date as a small tear-off calendar sheet. `tentative` shows only the month, with "verwacht". */
const props = defineProps({
  date: { type: String, required: true },
  tentative: { type: Boolean, default: false },
})

const parts = computed(() => dateParts(props.date))
</script>

<template>
  <span class="stamp" :class="{ tentative }" aria-hidden="true">
    <span class="weekday">{{ tentative ? t('releases.expected') : parts.weekday }}</span>
    <span class="day">{{ tentative ? '?' : parts.day }}</span>
    <span class="month">{{ parts.month }}</span>
  </span>
</template>

<style scoped>
.stamp {
  display: grid;
  justify-items: center;
  width: 4rem;
  overflow: hidden;
  border: 1.5px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.weekday {
  width: 100%;
  padding-block: var(--space-1);
  background: var(--accent);
  color: var(--accent-ink);
  font-size: var(--text-2xs);
  font-weight: 700;
  text-align: center;
}

.day {
  padding-top: var(--space-2);
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
}

.month {
  padding-bottom: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 600;
}

.tentative .day {
  color: var(--color-ink-soft);
}
</style>
