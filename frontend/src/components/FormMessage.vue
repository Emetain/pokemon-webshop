<script setup>
import { CircleAlert, CircleCheck } from 'lucide-vue-next'

defineProps({
  type: { type: String, default: 'error' },
})
</script>

<template>
  <p class="form-message" :class="type" :role="type === 'error' ? 'alert' : 'status'">
    <component :is="type === 'error' ? CircleAlert : CircleCheck" class="icon" :size="18" aria-hidden="true" />
    <span><slot /></span>
  </p>
</template>

<style scoped>
.form-message {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}

.icon {
  flex: none;
  margin-top: var(--space-half);
}

.error {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.success {
  background: var(--color-success-soft);
  color: var(--color-success);
}
</style>
