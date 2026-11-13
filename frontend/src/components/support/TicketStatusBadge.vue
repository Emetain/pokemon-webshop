<script setup>
import { t } from '../../utils/i18n'

/** Open (waiting for the shop), answered, or closed. */
defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <span class="badge" :class="status.toLowerCase()">{{ t(`support.status.${status}`) }}</span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  font-size: var(--text-xs);
  font-weight: 750;
  white-space: nowrap;
}

.open {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.answered {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.closed {
  background: var(--color-muted-soft);
  color: var(--color-ink-soft);
}
</style>
