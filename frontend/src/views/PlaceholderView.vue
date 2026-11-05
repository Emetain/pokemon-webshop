<script setup>
import { useRoute } from 'vue-router'
import { t } from '../utils/i18n'

const route = useRoute()
</script>

<template>
  <section class="container placeholder">
    <h1>{{ t(route.meta.titleKey) }}</h1>
    <p class="body">{{ t('placeholder.body') }}</p>
    <router-link to="/">{{ t('placeholder.backHome') }}</router-link>
  </section>
</template>

<style scoped>
.placeholder {
  padding-block: var(--space-7);
}

.body {
  margin-block: var(--space-2) var(--space-4);
  color: var(--color-ink-soft);
}
</style>
