<script setup>
import { t } from '../../utils/i18n'

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <span class="status" :class="status.toLowerCase().replace('_', '-')">{{ t(`orders.statuses.${status}`) }}</span>
</template>

<style scoped>
.status {
  display: inline-flex;
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-muted-soft);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 700;
  white-space: nowrap;
}

.paid,
.processing {
  background: var(--color-tint-strong);
  color: var(--color-brand-deep);
}

.shipped,
.delivered {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.pending-payment {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.cancelled,
.refunded {
  background: var(--color-muted-soft);
  color: var(--color-ink-soft);
}
</style>
