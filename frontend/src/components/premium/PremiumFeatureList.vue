<script setup>
import { BadgeCheck, ChartLine, ScanLine, Sparkles } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

/** What Premium adds. `compact` (the popup) shows the titles only, two to a row. */
defineProps({
  compact: { type: Boolean, default: false },
})

const FEATURES = [
  { icon: ScanLine, key: 'scanner' },
  { icon: ChartLine, key: 'history' },
  { icon: BadgeCheck, key: 'pregrade' },
  { icon: Sparkles, key: 'more' },
]
</script>

<template>
  <ul class="features" :class="{ compact }">
    <li v-for="feature in FEATURES" :key="feature.key" class="feature">
      <component :is="feature.icon" class="feature-icon" :size="compact ? 18 : 22" aria-hidden="true" />
      <div>
        <component :is="compact ? 'h3' : 'h2'" class="feature-title">{{ t(`premium.features.${feature.key}.title`) }}</component>
        <p v-if="!compact">{{ t(`premium.features.${feature.key}.text`) }}</p>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.features.compact {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
  gap: var(--space-2) var(--space-4);
}

.feature {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.compact .feature {
  gap: var(--space-2);
  align-items: center;
}

.feature-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-brand);
}

.compact .feature-icon {
  margin-top: 0;
}

.feature-title {
  font-size: var(--text-md);
}

.compact .feature-title {
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 650;
}

.feature p {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
