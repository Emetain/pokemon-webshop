<script setup>
import { ref, useId } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { t } from '../utils/i18n'
import { usePageMeta } from '../utils/pageMeta'

/**
 * For an address that doesn't exist: at the catch-all route, and inside the views of a product, drop, set, card or
 * article that wasn't found. Kept out of search engines. The backend answers 404 for these paths where it can.
 */
const router = useRouter()
const query = ref('')
const id = useId()

usePageMeta(() => ({ title: t('meta.notFound'), noindex: true }))

function search() {
  const q = query.value.trim()
  if (q) router.push({ name: 'search', query: { q } })
}
</script>

<template>
  <div class="container page">
    <h1>{{ t('notFound.title') }}</h1>
    <p class="text">{{ t('notFound.text') }}</p>

    <form class="search" role="search" @submit.prevent="search">
      <label class="label" :for="id">{{ t('notFound.searchLabel') }}</label>
      <div class="row">
        <input :id="id" v-model="query" class="input" type="search" :placeholder="t('notFound.searchPlaceholder')" />
        <button class="btn btn-primary" type="submit">
          <Search :size="18" aria-hidden="true" />
          {{ t('notFound.search') }}
        </button>
      </div>
    </form>

    <router-link class="btn btn-secondary" to="/">{{ t('notFound.home') }}</router-link>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  justify-items: start;
  gap: var(--space-4);
  padding-block: var(--space-6) var(--space-8);
}

.text {
  max-width: 60ch;
  color: var(--color-ink-soft);
}

.search {
  width: min(100%, 36rem);
}

.label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.input {
  flex: 1 1 14rem;
  min-width: 0;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.input:hover {
  border-color: var(--color-ink);
}
</style>
