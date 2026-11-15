<script setup>
import { computed, ref, useId } from 'vue'
import { CircleAlert } from 'lucide-vue-next'

const props = defineProps({
  label: { type: String, required: true },
  type: { type: String, default: 'text' },
  autocomplete: { type: String, default: 'off' },
  hint: { type: String, default: '' },
  /** What is wrong with this field. Read out with the field, and the field counts as invalid. */
  error: { type: String, default: '' },
})

const model = defineModel({ type: String, default: '' })
const id = useId()
const input = ref(null)

const describedBy = computed(
  () => [props.error && `${id}-error`, props.hint && `${id}-hint`].filter(Boolean).join(' ') || undefined,
)

/** For a form that moves to the field with a problem, so a screen reader reads its error. */
defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <div class="field">
    <label class="field-label" :for="id">{{ label }}</label>
    <input
      :id="id"
      ref="input"
      v-model="model"
      class="field-input"
      :class="{ invalid: error }"
      :type="type"
      :autocomplete="autocomplete"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
    />
    <p v-if="error" :id="`${id}-error`" class="field-error">
      <CircleAlert class="icon" :size="16" aria-hidden="true" />
      <span>{{ error }}</span>
    </p>
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

.field-input.invalid {
  border-color: var(--color-danger);
}

.field-error {
  display: flex;
  gap: var(--space-1);
  margin-top: var(--space-1);
  color: var(--color-danger);
  font-size: var(--text-xs);
  font-weight: 600;
}

.field-error .icon {
  flex: none;
  margin-top: 1px;
}

.field-hint {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
