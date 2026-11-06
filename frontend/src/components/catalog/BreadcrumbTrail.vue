<script setup>
import { ChevronRight } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

/** The last item is the current page and has no `to`. */
defineProps({
  items: { type: Array, required: true },
})
</script>

<template>
  <nav :aria-label="t('catalog.breadcrumbs')">
    <ol class="trail">
      <li v-for="item in items" :key="item.label" class="step">
        <router-link v-if="item.to" class="link" :to="item.to">{{ item.label }}</router-link>
        <span v-else aria-current="page">{{ item.label }}</span>
        <ChevronRight v-if="item.to" class="separator" :size="14" aria-hidden="true" />
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.trail {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-half) var(--space-1);
  margin: 0;
  padding: 0;
  font-size: var(--text-xs);
  list-style: none;
}

.step {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.link {
  color: inherit;
}

.separator {
  flex: none;
}
</style>
