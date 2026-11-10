<script setup>
import logoUrl from '../../assets/brand/poketopia-logo.webp'
import { t } from '../../utils/i18n'

/**
 * The Poketopia logo, linking to the home page. The file is a small copy of `docs/Poketopia-logo.png`: trimmed to the
 * emblem and three times the height the header shows it at, so it stays sharp on high-density screens. A parent can
 * set the height with --logo-height and, from 1024px, --logo-height-wide.
 */
</script>

<template>
  <router-link class="logo" to="/">
    <img class="logo-image" :src="logoUrl" :alt="t('brand.name')" width="386" height="288" />
  </router-link>
</template>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-sm);
}

.logo-image {
  display: block;
  width: auto;
  height: var(--logo-height, 2.75rem);
}

@media (min-width: 1024px) {
  .logo-image {
    height: var(--logo-height-wide, 3.5rem);
  }
}
</style>
