<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen } from 'lucide-vue-next'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import FormMessage from '../../components/FormMessage.vue'
import ProductFilters from '../../components/shop/ProductFilters.vue'
import ProductGrid from '../../components/shop/ProductGrid.vue'
import SeriesNav from '../../components/shop/SeriesNav.vue'
import { findGame } from '../../data/games'
import {
  ACCESSORY_TYPES,
  accessoryBySlug,
  accessoryLabel,
  eurosToCents,
  shopPath,
  typeBySlug,
  typePluralLabel,
} from '../../data/shop'
import { shopApi } from '../../services/api'
import { useShopNavigation } from '../../stores/shopNavigation'
import { filterQuery, pageFromQuery } from '../../utils/catalog'
import { formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const PAGE_SIZE = 24

/**
 * Every list of products: a game, a kind of product in a game, a series or set, accessories, pre-orders, and
 * all products or a search. Filters and sorting live in the URL, like on the card lists.
 */
const props = defineProps({
  /** `game`, `accessories`, `pre-order` or `all`. */
  mode: { type: String, default: 'all' },
  game: { type: String, default: '' },
  typeSlug: { type: String, default: '' },
  accessorySlug: { type: String, default: '' },
  series: { type: String, default: '' },
  setCode: { type: String, default: '' },
  q: { type: String, default: '' },
  sort: { type: String, default: '' },
  language: { type: String, default: '' },
  min: { type: String, default: '' },
  max: { type: String, default: '' },
  stock: { type: String, default: '' },
  sale: { type: String, default: '' },
  preorder: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const { seriesByGame, loadSeries } = useShopNavigation()

const result = ref(null)
const seriesDetail = ref(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')

const gameInfo = computed(() => (props.game ? findGame(props.game) : null))
const gameLabel = computed(() => (gameInfo.value ? t(gameInfo.value.labelKey) : ''))
const productType = computed(() => (props.typeSlug ? typeBySlug(props.typeSlug) : null))
const accessory = computed(() => (props.accessorySlug ? accessoryBySlug(props.accessorySlug) : null))
const preOrderPage = computed(() => props.mode === 'pre-order')
const defaultSort = computed(() => (preOrderPage.value ? 'release' : ''))
const unknownPath = computed(
  () => (props.typeSlug && !productType.value) || (props.accessorySlug && !accessory.value) || (props.game && !gameInfo.value),
)
const currentPage = computed(() => pageFromQuery(props.page))

const filters = computed(() => ({
  sort: props.sort,
  language: props.language,
  min: props.min,
  max: props.max,
  stock: props.stock === 'true',
  sale: props.sale === 'true',
  preorder: props.preorder === 'true',
}))

const hasFilters = computed(() => Object.entries(filters.value).some(([name, value]) => name !== 'sort' && value))

const currentSet = computed(() => seriesDetail.value?.sets.find((set) => set.code === props.setCode) ?? null)

const title = computed(() => {
  if (props.setCode) return currentSet.value?.name ?? props.setCode
  if (props.series) return seriesDetail.value?.name ?? ''
  if (productType.value) return typePluralLabel(productType.value.type)
  if (accessory.value) return accessoryLabel(accessory.value.type)
  if (props.mode === 'accessories') return t('shop.titles.accessories')
  if (preOrderPage.value) return t('shop.titles.preOrder')
  if (gameInfo.value) return gameLabel.value
  return props.q ? t('shop.titles.search', { query: props.q }) : t('shop.titles.all')
})

const intro = computed(() => {
  if (props.setCode) return currentSet.value ? t('shop.intro.set', { set: currentSet.value.name }) : ''
  if (props.series) return seriesDetail.value ? t('shop.intro.series', { series: seriesDetail.value.name }) : ''
  if (preOrderPage.value) return t('shop.intro.preOrder')
  if (props.mode === 'accessories' && !accessory.value) return t('shop.intro.accessories')
  if (gameInfo.value && !productType.value) return t('shop.intro.game', { game: gameLabel.value })
  return ''
})

const breadcrumbs = computed(() => {
  const items = [{ label: t('catalog.home'), to: '/' }]
  if (gameInfo.value && (productType.value || props.series)) items.push({ label: gameLabel.value, to: shopPath(props.game) })
  if (props.setCode && seriesDetail.value) {
    items.push({ label: seriesDetail.value.name, to: shopPath(props.game, { series: props.series }) })
  }
  if (props.mode === 'accessories' && accessory.value) items.push({ label: t('shop.titles.accessories'), to: '/accessoires' })
  items.push({ label: title.value || '…' })
  return items
})

/** Kinds of products to jump between, only those that exist here. */
const chips = computed(() => {
  const facets = result.value?.facets
  if (!facets) return []
  if (props.mode === 'accessories') {
    const present = new Set(facets.accessoryTypes)
    return [
      { key: 'all', label: t('shop.nav.all'), to: '/accessoires', current: !accessory.value },
      ...ACCESSORY_TYPES.filter((item) => present.has(item.type)).map((item) => ({
        key: item.type,
        label: accessoryLabel(item.type),
        to: `/accessoires/${item.slug}`,
        current: accessory.value?.type === item.type,
      })),
    ]
  }
  if (props.mode !== 'game' || props.series) return []
  return [
    { key: 'all', label: t('shop.nav.all'), to: shopPath(props.game), current: !productType.value },
    ...facets.types
      .filter((type) => type !== 'ACCESSORY')
      .map((type) => ({
        key: type,
        label: typePluralLabel(type),
        to: shopPath(props.game, { type }),
        current: productType.value?.type === type,
      })),
  ]
})

function applyFilters(changes) {
  router.push({ query: filterQuery({ q: props.q, ...filters.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ q: props.q, sort: props.sort }) })
}

const requestParams = computed(() => ({
  game: props.game || undefined,
  type: productType.value?.type ?? (props.mode === 'accessories' ? 'ACCESSORY' : undefined),
  accessory: accessory.value?.type,
  series: props.setCode ? undefined : props.series || undefined,
  set: props.setCode || undefined,
  q: props.q || undefined,
  sort: props.sort || defaultSort.value || undefined,
  language: props.language || undefined,
  minPrice: eurosToCents(props.min) ?? undefined,
  maxPrice: eurosToCents(props.max) ?? undefined,
  inStock: filters.value.stock || undefined,
  sale: filters.value.sale || undefined,
  preOrder: preOrderPage.value || filters.value.preorder || undefined,
  page: currentPage.value - 1,
  size: PAGE_SIZE,
}))

let latestRequest = 0

async function load() {
  if (unknownPath.value) {
    notFound.value = true
    loading.value = false
    return
  }
  const request = ++latestRequest
  loading.value = true
  notFound.value = false
  error.value = ''
  try {
    const value = await shopApi.products(requestParams.value)
    if (request === latestRequest) result.value = value
  } catch (exception) {
    if (request !== latestRequest) return
    result.value = null
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

let latestSeriesRequest = 0

async function loadSeriesDetail() {
  const request = ++latestSeriesRequest
  if (!props.game || !props.series) {
    seriesDetail.value = null
    return
  }
  try {
    const value = await shopApi.seriesDetail(props.game, props.series)
    if (request === latestSeriesRequest) seriesDetail.value = value
  } catch (exception) {
    if (request === latestSeriesRequest) {
      seriesDetail.value = null
      if (exception.status === 404) notFound.value = true
    }
  }
}

function onWished(product, wished) {
  product.wished = wished
}

watch(requestParams, load, { immediate: true, deep: true })
watch(() => [props.game, props.series], loadSeriesDetail, { immediate: true })
watch(
  () => props.game,
  (game) => {
    if (game && findGame(game)) loadSeries(game)
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="notFound" class="container">
    <CatalogNotice :title="t('shop.notFound.title')" :text="t('shop.notFound.text')">
      <router-link class="btn btn-primary" to="/pokemon">{{ t('home.browsePokemon') }}</router-link>
    </CatalogNotice>
  </div>

  <div v-else :data-game="gameInfo?.accent">
    <CatalogBand :accent="gameInfo?.accent ?? 'pokemon'">
      <BreadcrumbTrail :items="breadcrumbs" />
      <div class="heading">
        <div class="heading-text">
          <h1 class="title">{{ title }}</h1>
          <p v-if="intro" class="intro">{{ intro }}</p>
        </div>
        <span v-if="currentSet?.logoUrl" class="logo-plate">
          <img class="logo" :src="currentSet.logoUrl" alt="" decoding="async" />
        </span>
      </div>
      <nav v-if="chips.length > 2" class="chips" :aria-label="t('shop.nav.categories')">
        <router-link
          v-for="chip in chips"
          :key="chip.key"
          class="chip"
          :class="{ current: chip.current }"
          :to="chip.to"
          :aria-current="chip.current ? 'page' : undefined"
        >
          {{ chip.label }}
        </router-link>
      </nav>
    </CatalogBand>

    <div class="container page">
      <aside class="sidebar">
        <ProductFilters
          :values="filters"
          :languages="result?.facets.languages ?? []"
          :default-sort="defaultSort"
          :pre-order-page="preOrderPage"
          @change="applyFilters"
          @clear="clearFilters"
        />
        <SeriesNav
          v-if="mode === 'game' && gameInfo"
          :game="game"
          :series="seriesByGame[game] ?? []"
          :series-detail="seriesDetail"
          :set-code="setCode"
        />
      </aside>

      <section class="results" :aria-label="title">
        <CatalogNotice v-if="error" :title="error">
          <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
        </CatalogNotice>

        <template v-else>
          <div class="list-head">
            <p v-if="result && !loading" class="count" role="status">
              {{
                result.results.totalItems === 1
                  ? t('shop.oneProduct')
                  : t('shop.productCount', { count: formatNumber(result.results.totalItems) })
              }}
            </p>
            <router-link
              v-if="setCode && gameInfo"
              class="to-cards"
              :to="{ name: 'set', params: { game, setCode } }"
            >
              <BookOpen :size="16" aria-hidden="true" />
              {{ t('shop.toCards') }}
            </router-link>
          </div>

          <ProductGrid v-if="loading" loading narrow />

          <CatalogNotice
            v-else-if="!result?.results.items.length && hasFilters"
            :title="t('shop.empty.filterTitle')"
            :text="t('shop.empty.filterText')"
          >
            <button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('shop.filters.clear') }}</button>
          </CatalogNotice>

          <CatalogNotice
            v-else-if="!result?.results.items.length"
            :title="q ? t('shop.empty.filterTitle') : t('shop.empty.title')"
            :text="q ? t('shop.empty.searchText') : t('shop.empty.text')"
          >
            <router-link v-if="gameInfo && (productType || series)" class="btn btn-secondary" :to="shopPath(game)">
              {{ gameLabel }}
            </router-link>
          </CatalogNotice>

          <template v-else>
            <FormMessage v-if="error">{{ error }}</FormMessage>
            <ProductGrid :products="result.results.items" narrow @wished="onWished" @error="error = $event" />
            <PageLinks :page="currentPage" :total-pages="result.results.totalPages" />
          </template>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.heading-text {
  min-width: 0;
}

.intro {
  max-width: 60ch;
  margin-top: var(--space-1);
}

/* Logos are made for a light background, whatever the colour of the game. */
.logo-plate {
  display: none;
  flex: none;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.logo {
  display: block;
  width: 9rem;
  height: 3.5rem;
  object-fit: contain;
}

/* The kinds of products scroll sideways on a phone instead of wrapping into a wall. */
.chips {
  display: flex;
  gap: var(--space-2);
  margin: var(--space-4) calc(var(--gutter) * -1) 0;
  padding: 0 var(--gutter) var(--space-1);
  overflow-x: auto;
  scrollbar-width: none;
}

.chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-target);
  padding-inline: var(--space-4);
  border-radius: var(--radius-round);
  background: rgb(255 255 255 / 14%);
  box-shadow: inset 0 0 0 1.5px rgb(255 255 255 / 40%);
  color: var(--accent-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.chip:hover {
  box-shadow: inset 0 0 0 2px var(--accent-ink);
}

.chip.current {
  background: var(--color-surface);
  box-shadow: none;
  color: var(--accent);
}

.page {
  display: grid;
  gap: var(--space-4);
  padding-block: var(--space-4) 0;
}

.sidebar {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
}

.results {
  min-width: 0;
}

.list-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  min-height: var(--touch-target);
  margin-bottom: var(--space-3);
}

.count {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.to-cards {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.to-cards:hover {
  text-decoration: underline;
}

@media (min-width: 640px) {
  .logo-plate {
    display: block;
  }
}

@media (min-width: 1024px) {
  .chips {
    flex-wrap: wrap;
    margin-inline: 0;
    padding-inline: 0;
    overflow: visible;
  }

  .page {
    grid-template-columns: 15rem minmax(0, 1fr);
    gap: var(--space-6);
    padding-top: var(--space-5);
  }

  .sidebar {
    gap: var(--space-5);
  }
}
</style>
