<script setup>
import { useLiveDrops } from '../../stores/drops'
import { t } from '../../utils/i18n'

/** "LIVE" next to Drops in the menu while a drop is live. */
const liveDrops = useLiveDrops()
liveDrops.start()
</script>

<template>
  <span v-if="liveDrops.isLive.value" class="live-badge">
    <span class="live-dot" aria-hidden="true"></span>
    <span aria-hidden="true">{{ t('nav.liveBadge') }}</span>
    <span class="visually-hidden">{{ t('nav.liveBadgeLabel') }}</span>
  </span>
</template>

<style scoped>
.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3em;
  padding: 0.1em 0.5em;
  border-radius: var(--radius-round);
  background: var(--color-sale);
  color: var(--color-on-brand);
  font-size: var(--text-2xs);
  font-weight: 800;
  letter-spacing: 0.05em;
  line-height: 1.5;
  text-transform: uppercase;
}

.live-dot {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: var(--radius-round);
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.3;
  }
}

@media (prefers-reduced-motion: reduce) {
  .live-dot {
    animation: none;
  }
}
</style>
