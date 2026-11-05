<script setup>
import { t } from '../../utils/i18n'
</script>

<template>
  <router-link class="logo" to="/">
    <span class="logo-card" aria-hidden="true"></span>
    <span>{{ t('brand.name') }}</span>
  </router-link>
</template>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-on-binder);
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 85%;
  font-weight: 800;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
}

.logo-card {
  width: 1.1rem;
  aspect-ratio: 5 / 7;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
  transform: rotate(-8deg);
}
</style>
