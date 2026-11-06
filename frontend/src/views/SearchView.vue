<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CardFilters from '../components/catalog/CardFilters.vue'
import CardGrid from '../components/catalog/CardGrid.vue'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import CatalogNotice from '../components/catalog/CatalogNotice.vue'
import FilterSelect from '../components/catalog/FilterSelect.vue'
import PageLinks from '../components/catalog/PageLinks.vue'
import { GAMES } from '../data/games'
import { catalogApi } from '../services/api'
import { useSession } from '../stores/session'
import { cardCountLabel, filterQuery, pageFromQuery } from '../utils/catalog'
import { t } from '../utils/i18n'

const MIN_QUERY_LENGTH = 2

const props = defineProps({
  q: { type: String, default: '' },
  game: { type: String, default: '' },
  sort: { type: String, default: '' },
  category: { type: String, default: '' },
  color: { type: String, default: '' },
  rarity: { type: String, default: '' },
  variant: { type: String, default: '' },
  priced: { type: String, default: '' },
  collection: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const session = useSession()

const result = ref(null)
const loading = ref(false)
const error = ref('')

const query = computed(() => props.q.trim())
const queryTooShort = computed(() => query.value.length > 0 && query.value.length < MIN_QUERY_LENGTH)
const canSearch = computed(() => query.value.length >= MIN_QUERY_LENGTH)
const currentPage = computed(() => pageFromQuery(props.page))
const filters = computed(() => ({
  game: props.game,
  sort: props.sort,
  category: props.category,
  color: props.color,
  rarity: props.rarity,
  variant: props.variant,
  priced: props.priced === 'true',
  collection: props.collection,
}))

const hasFilters = computed(() => Object.entries(filters.value).some(([name, value]) => name !== 'sort' && value))

const gameOptions = GAMES.map((game) => ({ value: game.slug, label: t(game.labelKey) }))

function applyFilters(changes) {
  router.push({ query: filterQuery({ q: props.q, ...filters.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ q: props.q, sort: props.sort }) })
}

const gameFilter = computed({
  get: () => props.game,
  // Rarities, kinds and colours differ per game, so a choice for one game means nothing in the next.
  set: (game) => applyFilters({ game, category: '', color: '', rarity: '', variant: '' }),
})

let latestRequest = 0

async function search() {
  const request = ++latestRequest
  error.value = ''
  if (!canSearch.value) {
    result.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    const found = await catalogApi.search({
      q: query.value,
      ...filters.value,
      page: currentPage.value - 1,
    })
    if (request === latestRequest) result.value = found
  } catch (exception) {
    if (request !== latestRequest) return
    result.value = null
    error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

function onCollectionChange(card, change) {
  card.owned = change.entries
}

watch(() => [query.value, filters.value, currentPage.value], search, { immediate: true })
</script>

<template>
  <CatalogBand>
    <h1>{{ canSearch ? t('search.resultsTitle', { query }) : t('search.title') }}</h1>
    <p v-if="!canSearch" class="prompt">{{ queryTooShort ? t('search.tooShort') : t('search.prompt') }}</p>
  </CatalogBand>

  <div v-if="canSearch" class="container page">
    <CatalogNotice v-if="error" :title="error">
      <button class="btn btn-secondary" type="button" @click="search">{{ t('catalog.retry') }}</button>
    </CatalogNotice>

    <template v-else>
      <CardFilters
        :game="game"
        :values="filters"
        :options="result ?? {}"
        default-sort="best-match"
        :tracking="session.isLoggedIn.value"
        @change="applyFilters"
        @clear="clearFilters"
      >
        <FilterSelect
          v-model="gameFilter"
          :label="t('catalog.filters.game')"
          :all-label="t('catalog.filters.allGames')"
          :options="gameOptions"
        />
      </CardFilters>
      <p v-if="result && !loading" class="count" role="status">{{ cardCountLabel(result.results.totalItems) }}</p>

      <CardGrid v-if="loading" loading />

      <CatalogNotice
        v-else-if="!result?.results.items.length"
        :title="t('search.emptyTitle')"
        :text="t('search.emptyText')"
      >
        <button v-if="hasFilters" class="btn btn-secondary" type="button" @click="clearFilters">
          {{ t('catalog.filters.clear') }}
        </button>
      </CatalogNotice>

      <template v-else>
        <CardGrid :cards="result.results.items" show-set @change="onCollectionChange" />
        <PageLinks :page="currentPage" :total-pages="result.results.totalPages" />
      </template>
    </template>
  </div>
</template>

<style scoped>
.prompt {
  margin-top: var(--space-1);
}

.page {
  padding-block: var(--space-4) 0;
}

.count {
  margin-block: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
