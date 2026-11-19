<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CardFilters from '../components/catalog/CardFilters.vue'
import FormMessage from '../components/FormMessage.vue'
import CardGrid from '../components/catalog/CardGrid.vue'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import CatalogNotice from '../components/catalog/CatalogNotice.vue'
import FilterSelect from '../components/catalog/FilterSelect.vue'
import ListEnd from '../components/catalog/ListEnd.vue'
import ProductGrid from '../components/shop/ProductGrid.vue'
import { GAMES } from '../data/games'
import { catalogApi, shopApi } from '../services/api'
import { formatNumber } from '../utils/format'
import { useSession } from '../stores/session'
import { cardCountLabel, filterQuery, pageFromQuery } from '../utils/catalog'
import { t } from '../utils/i18n'
import { useInfiniteList } from '../utils/infiniteList'

const MIN_QUERY_LENGTH = 2
const PRODUCT_PREVIEW = 6

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

/** The filter choices that come with the results: rarities, kinds and colours of what was found. */
const options = ref(null)
const products = ref(null)
const productError = ref('')

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

const EMPTY = { items: [], totalItems: 0, totalPages: 0 }

/** The cards found, 25 at a time while the customer scrolls (phase 11). */
const results = reactive(
  useInfiniteList({
    fetchPage: async (page, size) => {
      if (!canSearch.value) return EMPTY
      const found = await catalogApi.search({ q: query.value, ...filters.value, page, size })
      if (page === 0) options.value = found
      return found.results
    },
    key: () => [query.value, filters.value],
    page: () => currentPage.value,
  }),
)

const error = computed(() => results.error?.message ?? '')

function onCollectionChange(card, change) {
  card.owned = change.entries
}

let latestProductsRequest = 0

/** Products that match go above the cards. A failure leaves just the cards. */
async function searchProducts() {
  const request = ++latestProductsRequest
  if (!canSearch.value) {
    products.value = null
    return
  }
  try {
    const value = await shopApi.products({ q: query.value, game: props.game || undefined, size: PRODUCT_PREVIEW })
    if (request === latestProductsRequest) products.value = value.results
  } catch {
    if (request === latestProductsRequest) products.value = null
  }
}

const allProductsLink = computed(() => ({ path: props.game ? `/${props.game}` : '/producten', query: { q: query.value } }))

watch(() => [query.value, props.game], searchProducts, { immediate: true })
</script>

<template>
  <CatalogBand>
    <h1>{{ canSearch ? t('search.resultsTitle', { query }) : t('search.title') }}</h1>
    <p v-if="!canSearch" class="prompt">{{ queryTooShort ? t('search.tooShort') : t('search.prompt') }}</p>
  </CatalogBand>

  <div v-if="canSearch" class="container page">
    <CatalogNotice v-if="error" :title="error">
      <button class="btn btn-secondary" type="button" @click="results.refresh()">{{ t('catalog.retry') }}</button>
    </CatalogNotice>

    <template v-else>
      <section v-if="products?.items.length" class="products" aria-labelledby="search-products">
        <div class="section-head">
          <h2 id="search-products">{{ t('search.products') }}</h2>
          <router-link v-if="products.totalItems > products.items.length" class="all" :to="allProductsLink">
            {{ t('search.allProducts', { count: formatNumber(products.totalItems) }) }}
          </router-link>
        </div>
        <FormMessage v-if="productError" class="product-error">{{ productError }}</FormMessage>
        <ProductGrid
          :products="products.items"
          @wished="(product, wished) => (product.wished = wished)"
          @error="productError = $event"
        />
      </section>

      <h2 v-if="products?.items.length" class="cards-title">{{ t('search.cards') }}</h2>
      <CardFilters
        :game="game"
        :values="filters"
        :options="options ?? {}"
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
      <p v-if="results.loaded && !results.loading" class="count" role="status">{{ cardCountLabel(results.totalItems) }}</p>

      <CardGrid v-if="results.loading" loading />

      <CatalogNotice
        v-else-if="!results.items.length"
        :title="t('search.emptyTitle')"
        :text="t('search.emptyText')"
      >
        <button v-if="hasFilters" class="btn btn-secondary" type="button" @click="clearFilters">
          {{ t('catalog.filters.clear') }}
        </button>
      </CatalogNotice>

      <template v-else>
        <CardGrid :cards="results.items" show-set @change="onCollectionChange" />
        <ListEnd :list="results" />
      </template>
    </template>
  </div>
</template>

<style scoped>
.products {
  margin-bottom: var(--space-7);
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  margin-bottom: var(--space-4);
}

.all {
  font-size: var(--text-sm);
  font-weight: 650;
}

.cards-title {
  margin-bottom: var(--space-4);
}

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

.product-error {
  margin-bottom: var(--space-3);
}
</style>
