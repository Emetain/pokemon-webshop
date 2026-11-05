<script setup>
import { useId } from 'vue'
import { COUNTRY_CODES } from '../../data/countries'
import { t } from '../../utils/i18n'

defineProps({
  label: { type: String, required: true },
})

const model = defineModel({ type: String, required: true })
const id = useId()
</script>

<template>
  <div>
    <label class="select-label" :for="id">{{ label }}</label>
    <select :id="id" v-model="model" class="select-input">
      <option v-for="code in COUNTRY_CODES" :key="code" :value="code">{{ t(`countries.${code}`) }}</option>
    </select>
  </div>
</template>

<style scoped>
.select-label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.select-input {
  width: 100%;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}
</style>
