<script setup>
import { Minus, Plus } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

defineProps({
  quantity: { type: Number, required: true },
  /** What is being counted, for screen readers. */
  name: { type: String, required: true },
  disabled: { type: Boolean, default: false },
})

defineEmits(['increase', 'decrease'])
</script>

<template>
  <div class="stepper">
    <button
      class="step"
      type="button"
      :disabled="disabled"
      :aria-label="t('collection.decrease', { name })"
      @click="$emit('decrease')"
    >
      <Minus :size="16" aria-hidden="true" />
    </button>
    <output class="quantity" :aria-label="t('collection.quantity')">{{ quantity }}</output>
    <button
      class="step"
      type="button"
      :disabled="disabled"
      :aria-label="t('collection.increase', { name })"
      @click="$emit('increase')"
    >
      <Plus :size="16" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.stepper {
  display: inline-grid;
  grid-template-columns: var(--touch-target) minmax(1.75rem, 1fr) var(--touch-target);
  align-items: center;
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.step {
  display: flex;
  align-items: center;
  justify-content: center;
  height: calc(var(--touch-target) - 3px);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
}

.step:hover:not(:disabled) {
  background: var(--color-tint);
  color: var(--color-brand);
}

.step:disabled {
  cursor: progress;
  opacity: 0.5;
}

.quantity {
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  text-align: center;
}
</style>
