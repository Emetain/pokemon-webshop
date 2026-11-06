<script setup>
import { useId } from 'vue'

/** `options` are `{ value, label }`. The empty value means "no filter", with `allLabel` as its text. */
defineProps({
  label: { type: String, required: true },
  allLabel: { type: String, required: true },
  options: { type: Array, required: true },
})

const model = defineModel({ type: String, default: '' })
const id = useId()
</script>

<template>
  <div class="filter">
    <label class="filter-label" :for="id">{{ label }}</label>
    <select :id="id" v-model="model" class="filter-input">
      <option value="">{{ allLabel }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
  </div>
</template>

<style scoped>
.filter {
  min-width: 0;
}

.filter-label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  font-weight: 600;
}

.filter-input {
  width: 100%;
  height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: var(--text-sm);
}

.filter-input:hover {
  border-color: var(--color-ink);
}
</style>
