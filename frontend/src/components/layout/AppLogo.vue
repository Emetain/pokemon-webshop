<script setup>
import BallMark from '../BallMark.vue'
import { t } from '../../utils/i18n'

defineProps({
  inverse: { type: Boolean, default: false },
})
</script>

<template>
  <router-link class="logo" :class="{ inverse }" to="/">
    <BallMark class="logo-mark" />
    <span>{{ t('brand.name') }}</span>
  </router-link>
</template>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-ink);
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 85%;
  font-weight: 800;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
}

.logo-mark {
  flex: none;
  width: 1.75rem;
  height: 1.75rem;
}

.logo.inverse {
  color: var(--color-on-brand);
}

.logo.inverse .logo-mark {
  --ball-line: var(--color-brand-deep);
}

@media (min-width: 1024px) {
  .logo {
    font-size: var(--text-xl);
  }

  .logo-mark {
    width: 2.25rem;
    height: 2.25rem;
  }
}
</style>
