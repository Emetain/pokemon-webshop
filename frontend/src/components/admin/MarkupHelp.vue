<script setup>
import { t } from '../../utils/i18n'
</script>

<template>
  <details class="help">
    <summary>{{ t('admin.markup.title') }}</summary>
    <ul>
      <li v-for="line in t('admin.markup.lines')" :key="line"><code>{{ line }}</code></li>
    </ul>
  </details>
</template>

<style scoped>
.help {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

summary {
  min-height: var(--touch-target);
  display: flex;
  align-items: center;
  cursor: pointer;
  font-weight: 600;
}

ul {
  display: grid;
  gap: var(--space-1);
  padding-left: var(--space-4);
}
</style>
