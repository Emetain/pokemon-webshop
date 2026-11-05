<script setup>
import { useId } from 'vue'

defineProps({
  label: { type: String, required: true },
  type: { type: String, default: 'text' },
  autocomplete: { type: String, default: 'off' },
  hint: { type: String, default: '' },
})

const model = defineModel({ type: String, default: '' })
const id = useId()
</script>

<template>
  <div class="field">
    <label class="field-label" :for="id">{{ label }}</label>
    <input
      :id="id"
      v-model="model"
      class="field-input"
      :type="type"
      :autocomplete="autocomplete"
      :aria-describedby="hint ? `${id}-hint` : undefined"
    />
    <p v-if="hint" :id="`${id}-hint`" class="field-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.field-label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.field-input {
  width: 100%;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.field-input:hover {
  border-color: var(--color-ink);
}

.field-hint {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
