<script setup>
import { computed } from 'vue'
import FormMessage from '../FormMessage.vue'
import PageLinks from './PageLinks.vue'
import { t } from '../../utils/i18n'

/**
 * The end of a list that keeps loading (phase 11, `useInfiniteList`): the point that starts the next load, then
 * "Meer laden" after a few loads so the footer stays reachable, and plain page links for search engines, which
 * don't scroll.
 */
const props = defineProps({
  /** A `reactive(useInfiniteList(...))`. */
  list: { type: Object, required: true },
})

const failed = computed(() => props.list.moreError?.message ?? (props.list.moreError ? t('catalog.list.failed') : ''))
</script>

<template>
  <div class="list-end">
    <div :ref="(element) => (list.sentinel = element)" class="sentinel" aria-hidden="true"></div>
    <p v-if="list.loadingMore" class="status" role="status">{{ t('catalog.list.loading') }}</p>
    <FormMessage v-if="failed">{{ failed }}</FormMessage>
    <button v-if="list.showMoreButton && !list.loadingMore" class="btn btn-secondary more" type="button" @click="list.loadMore()">
      {{ t('catalog.list.more') }}
    </button>
    <nav v-if="list.totalPages > 1" class="pages" :aria-label="t('catalog.list.pages')">
      <PageLinks :page="list.loadedPages" :total-pages="list.totalPages" />
    </nav>
  </div>
</template>

<style scoped>
.list-end {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.sentinel {
  width: 100%;
  height: 1px;
}

.status {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.more {
  min-width: 12rem;
}

/* For search engines and keyboard users; the list loads by itself for everyone else. */
.pages {
  opacity: 0.75;
  font-size: var(--text-sm);
}
</style>
