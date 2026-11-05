<script setup>
import AppFooter from './AppFooter.vue'
import AppHeader from './AppHeader.vue'
import { t } from '../../utils/i18n'
</script>

<template>
  <div class="shop">
    <a class="skip-link" href="#main">{{ t('layout.skipToContent') }}</a>
    <AppHeader />
    <main id="main" class="shop-main" tabindex="-1">
      <router-view />
    </main>
    <AppFooter />
  </div>
</template>

<style scoped>
.shop {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.shop-main {
  flex: 1;
}

.skip-link {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  z-index: 10;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  transform: translateY(-200%);
}

.skip-link:focus {
  transform: none;
}
</style>
