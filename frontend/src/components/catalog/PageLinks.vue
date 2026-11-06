<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { t } from '../../utils/i18n'

/** Real links, so a page can be shared and opened in a new tab. `page` starts at 1, like in the URL. */
const props = defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
})

const route = useRoute()

/** The first, last and current page with their neighbours. `near` pages drop out on small screens. */
const items = computed(() => {
  const { page, totalPages } = props
  const numbers = [...new Set([1, page - 1, page, page + 1, totalPages])]
    .filter((number) => number >= 1 && number <= totalPages)
    .sort((a, b) => a - b)

  return numbers.flatMap((number, index) => {
    const gap = index > 0 && number - numbers[index - 1] > 1
    const link = { number, near: Math.abs(number - page) === 1 && number !== 1 && number !== totalPages }
    return gap ? [{ gap: number }, link] : [link]
  })
})

function pageLink(number) {
  return { query: { ...route.query, page: number > 1 ? number : undefined } }
}
</script>

<template>
  <nav v-if="totalPages > 1" class="pages" :aria-label="t('catalog.pagination.label')">
    <router-link
      v-if="page > 1"
      class="page step"
      :to="pageLink(page - 1)"
      :aria-label="t('catalog.pagination.previous')"
    >
      <ChevronLeft :size="18" aria-hidden="true" />
    </router-link>

    <template v-for="item in items" :key="item.number ?? `gap-${item.gap}`">
      <span v-if="item.gap" class="gap" aria-hidden="true">…</span>
      <router-link
        v-else
        class="page"
        :class="{ near: item.near, current: item.number === page }"
        :to="pageLink(item.number)"
        :aria-label="t('catalog.pagination.page', { page: item.number })"
        :aria-current="item.number === page ? 'page' : undefined"
      >
        {{ item.number }}
      </router-link>
    </template>

    <router-link
      v-if="page < totalPages"
      class="page step"
      :to="pageLink(page + 1)"
      :aria-label="t('catalog.pagination.next')"
    >
      <ChevronRight :size="18" aria-hidden="true" />
    </router-link>
  </nav>
</template>

<style scoped>
.pages {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  margin-top: var(--space-6);
}

.page {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--touch-target);
  height: var(--touch-target);
  padding-inline: var(--space-1);
  border-radius: var(--radius-sm);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  text-decoration: none;
}

.page:hover {
  background: var(--color-tint);
  color: var(--color-brand);
}

.page.current {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.page.near {
  display: none;
}

.gap {
  color: var(--color-ink-soft);
}

@media (min-width: 480px) {
  .page.near {
    display: inline-flex;
  }
}
</style>
