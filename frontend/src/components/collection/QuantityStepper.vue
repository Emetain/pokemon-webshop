<script setup>
import { Minus, Plus } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

/**
 * Starts as a single + button. Once there are copies, the count and a - appear in the same element, on the left
 * of that same button, so the + never moves and can be pressed again right away.
 */
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
    <template v-if="quantity > 0">
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
    </template>
    <button
      class="step"
      type="button"
      :disabled="disabled"
      :aria-label="quantity > 0 ? t('collection.increase', { name }) : t('collection.addCard', { name })"
      @click="$emit('increase')"
    >
      <Plus :size="18" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.stepper {
  display: inline-flex;
  flex: none;
  align-items: center;
  border: 1.5px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-brand);
}

.step {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: calc(var(--touch-target) - 3px);
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.step:hover:not(:disabled) {
  background: var(--color-tint);
}

.step:disabled {
  cursor: progress;
  opacity: 0.5;
}

.quantity {
  min-width: 1.25rem;
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 700;
  text-align: center;
}
</style>
