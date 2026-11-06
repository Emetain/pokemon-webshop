<script setup>
defineProps({
  title: { type: String, required: true },
  text: { type: String, default: '' },
})
</script>

<template>
  <div class="notice">
    <h2 class="notice-title">{{ title }}</h2>
    <p v-if="text" class="notice-text">{{ text }}</p>
    <slot />
  </div>
</template>

<style scoped>
.notice {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding-block: var(--space-6);
}

.notice-title {
  font-size: var(--text-lg);
}

.notice-text {
  max-width: 60ch;
  color: var(--color-ink-soft);
}
</style>
