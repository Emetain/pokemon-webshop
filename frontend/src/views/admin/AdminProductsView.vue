<script setup>
import { computed, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from 'lucide-vue-next'
import PageLinks from '../../components/catalog/PageLinks.vue'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { PRODUCT_TYPES, accessoryLabel, languageLabel, typeLabel } from '../../data/shop'
import { adminShopApi } from '../../services/api'
import { statusLabel } from '../../utils/admin'
import { filterQuery, pageFromQuery } from '../../utils/catalog'
import { formatMoney, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const SEARCH_DELAY_MS = 350
const PAGE_SIZES = [25, 50, 100]

/** Filters, sorting and page live in the URL, so the list can be bookmarked. */
const props = defineProps({
  q: { type: String, default: '' },
  game: { type: String, default: '' },
  type: { type: String, default: '' },
  status: { type: String, default: '' },
  stock: { type: String, default: '' },
  source: { type: String, default: '' },
  price: { type: String, default: '' },
  sort: { type: String, default: '' },
  size: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const ids = {
  search: useId(),
  game: useId(),
  type: useId(),
  status: useId(),
  stock: useId(),
  source: useId(),
  sort: useId(),
  size: useId(),
}

const products = ref(null)
const loading = ref(false)
const error = ref('')
const searchText = ref(props.q)

const pageSize = computed(() => (PAGE_SIZES.includes(Number(props.size)) ? Number(props.size) : PAGE_SIZES[0]))
const currentPage = computed(() => pageFromQuery(props.page))
const values = computed(() => ({
  q: props.q,
  game: props.game,
  type: props.type,
  status: props.status,
  stock: props.stock,
  source: props.source,
  price: props.price,
  sort: props.sort,
  size: props.size,
}))
const hasFilters = computed(() =>
  Boolean(props.q || props.game || props.type || props.status || props.stock || props.source || props.price),
)

function apply(changes) {
  router.push({ query: filterQuery({ ...values.value, ...changes }) })
}

function filter(name) {
  return computed({ get: () => props[name], set: (value) => apply({ [name]: value }) })
}

const gameFilter = filter('game')
const typeFilter = filter('type')
const statusFilter = filter('status')
const stockFilter = filter('stock')
const sourceFilter = filter('source')
const sortFilter = filter('sort')
const sizeFilter = computed({
  get: () => String(pageSize.value),
  set: (value) => apply({ size: value === String(PAGE_SIZES[0]) ? '' : value }),
})

let searchTimer = null
watch(searchText, (text) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (text.trim() !== props.q) apply({ q: text.trim() })
  }, SEARCH_DELAY_MS)
})
watch(() => props.q, (q) => (searchText.value = q))

function clear() {
  searchText.value = ''
  router.push({ query: filterQuery({ sort: props.sort, size: props.size }) })
}

let latestRequest = 0

async function load() {
  const request = ++latestRequest
  loading.value = true
  error.value = ''
  try {
    const result = await adminShopApi.products({ ...values.value, size: pageSize.value, page: currentPage.value - 1 })
    if (request === latestRequest) products.value = result
  } catch (exception) {
    if (request === latestRequest) error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

const kindOf = (product) => (product.accessoryType ? accessoryLabel(product.accessoryType) : typeLabel(product.type))

const gameLabel = (slug) => {
  const game = GAMES.find((item) => item.slug === slug)
  return game ? t(game.labelKey) : ''
}

const countText = computed(() =>
  products.value?.totalItems === 1
    ? t('admin.products.oneCount')
    : t('admin.products.count', { count: formatNumber(products.value?.totalItems ?? 0) }),
)

watch(() => [values.value, currentPage.value], load, { immediate: true, deep: true })
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.products.title') }}</h1>
      <p class="admin-intro">{{ t('admin.products.intro') }}</p>
    </div>
    <div class="head-actions">
      <router-link class="btn btn-primary btn-small" to="/admin/producten/nieuw">
        <Plus :size="18" aria-hidden="true" />
        {{ t('admin.products.add') }}
      </router-link>
      <router-link class="btn btn-secondary btn-small" to="/admin/producten/losse-kaart">
        {{ t('admin.products.addSingle') }}
      </router-link>
    </div>
  </div>

  <div class="filters">
    <div class="admin-field grow">
      <label class="admin-label" :for="ids.search">{{ t('admin.products.search') }}</label>
      <input :id="ids.search" v-model="searchText" class="admin-input" type="search" autocomplete="off" />
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.game">{{ t('admin.products.game') }}</label>
      <select :id="ids.game" v-model="gameFilter" class="admin-input">
        <option value="">{{ t('admin.products.allGames') }}</option>
        <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
      </select>
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.type">{{ t('admin.products.type') }}</label>
      <select :id="ids.type" v-model="typeFilter" class="admin-input">
        <option value="">{{ t('admin.products.allTypes') }}</option>
        <option v-for="item in PRODUCT_TYPES" :key="item.type" :value="item.type">{{ typeLabel(item.type) }}</option>
      </select>
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.status">{{ t('admin.products.status') }}</label>
      <select :id="ids.status" v-model="statusFilter" class="admin-input">
        <option value="">{{ t('admin.products.allStatuses') }}</option>
        <option v-for="status in ['ACTIVE', 'DRAFT', 'ARCHIVED']" :key="status" :value="status">
          {{ statusLabel(status) }}
        </option>
      </select>
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.stock">{{ t('admin.products.stock') }}</label>
      <select :id="ids.stock" v-model="stockFilter" class="admin-input">
        <option value="">{{ t('admin.products.allStock') }}</option>
        <option value="in">{{ t('admin.products.stockIn') }}</option>
        <option value="out">{{ t('admin.products.stockOut') }}</option>
        <option value="low">{{ t('admin.products.stockLow') }}</option>
      </select>
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.source">{{ t('admin.products.source') }}</label>
      <select :id="ids.source" v-model="sourceFilter" class="admin-input">
        <option value="">{{ t('admin.products.allSources') }}</option>
        <option value="imported">{{ t('admin.products.imported') }}</option>
        <option value="manual">{{ t('admin.products.manual') }}</option>
      </select>
    </div>
    <div class="admin-field">
      <label class="admin-label" :for="ids.sort">{{ t('admin.products.sort') }}</label>
      <select :id="ids.sort" v-model="sortFilter" class="admin-input">
        <option value="">{{ t('admin.products.sortUpdated') }}</option>
        <option value="new">{{ t('admin.products.sortNew') }}</option>
        <option value="name">{{ t('admin.products.sortName') }}</option>
        <option value="price">{{ t('admin.products.sortPrice') }}</option>
      </select>
    </div>
    <label class="admin-check">
      <input type="checkbox" :checked="price === 'missing'" @change="apply({ price: $event.target.checked ? 'missing' : '' })" />
      {{ t('admin.products.withoutPrice') }}
    </label>
    <button v-if="hasFilters" class="link-button" type="button" @click="clear">{{ t('admin.products.clear') }}</button>
  </div>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <div class="list-bar">
    <p v-if="products" class="count" role="status">{{ countText }}</p>
    <label class="per-page">
      {{ t('admin.products.perPage') }}
      <select :id="ids.size" v-model="sizeFilter" class="admin-input">
        <option v-for="option in PAGE_SIZES" :key="option" :value="String(option)">{{ option }}</option>
      </select>
    </label>
  </div>

  <p v-if="loading && !products" class="count">{{ t('admin.images.loading') }}</p>
  <p v-else-if="products && !products.items.length" class="count">
    {{ hasFilters ? t('admin.products.noResults') : t('admin.products.empty') }}
  </p>

  <div v-else-if="products" class="admin-table-scroll" :aria-busy="loading">
    <table class="admin-table">
      <thead>
        <tr>
          <th scope="col">{{ t('admin.products.product') }}</th>
          <th scope="col">{{ t('admin.products.status') }}</th>
          <th class="num" scope="col">{{ t('admin.products.price') }}</th>
          <th class="num" scope="col">{{ t('admin.products.onHand') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="product in products.items" :key="product.id">
          <th scope="row" class="product-cell">
            <span class="thumb">
              <img v-if="product.imageUrl" :src="product.imageUrl" alt="" loading="lazy" decoding="async" />
            </span>
            <span class="product-text">
              <router-link
                :to="`/admin/producten/${product.id}`"
                :aria-label="t('admin.products.editLabel', { name: product.name })"
              >
                {{ product.name }}
              </router-link>
              <span class="facts">
                <span>{{ kindOf(product) }}</span>
                <span v-if="product.game">{{ gameLabel(product.game) }}</span>
                <span v-if="product.language">{{ languageLabel(product.language) }}</span>
                <span v-if="product.setName">{{ product.setName }}</span>
                <span v-if="product.importSource">{{ t(`admin.products.sources.${product.importSource}`) }}</span>
              </span>
            </span>
          </th>
          <td>
            <span class="status" :class="product.status.toLowerCase()">{{ statusLabel(product.status) }}</span>
            <span v-if="product.preOrder" class="status pre-order">{{ t('shop.badges.preOrder') }}</span>
          </td>
          <td class="num">
            <template v-if="product.priceCents !== null">
              {{ formatMoney(product.salePriceCents ?? product.priceCents, 'EUR') }}
              <del v-if="product.salePriceCents !== null" class="old">{{ formatMoney(product.priceCents, 'EUR') }}</del>
            </template>
            <span v-else class="muted">{{ t('admin.products.noPriceYet') }}</span>
          </td>
          <td class="num">
            <span :class="{ low: product.available <= product.lowStockThreshold && product.onHand > 0, out: product.available <= 0 }">
              {{ formatNumber(product.available) }}
            </span>
            <span v-if="product.reserved" class="reserved">{{ t('admin.products.reserved', { count: product.reserved }) }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <PageLinks v-if="products" :page="currentPage" :total-pages="products.totalPages" />
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.filters .admin-field {
  flex: 1 1 9rem;
  max-width: 13rem;
}

.filters .admin-field.grow {
  flex: 2 1 14rem;
  max-width: 22rem;
}

.message {
  margin-top: var(--space-3);
}

.list-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
  margin-top: var(--space-4);
}

.count {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.list-bar .count {
  margin-top: 0;
}

.per-page {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
  font-size: var(--text-sm);
  font-weight: 600;
}

.per-page .admin-input {
  width: auto;
  min-width: 5rem;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 16rem;
  font-weight: 550;
}

.thumb {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius-sm);
  background: var(--color-tint);
}

.thumb img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.product-text {
  display: grid;
  min-width: 0;
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 400;
}

.status {
  display: inline-block;
  margin: var(--space-half) var(--space-1) var(--space-half) 0;
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-xs);
  font-size: var(--text-2xs);
  font-weight: 700;
  white-space: nowrap;
}

.status.active {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.status.draft,
.status.archived {
  background: var(--color-muted-soft);
  color: var(--color-ink-soft);
}

.status.pre-order {
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
}

.old,
.muted,
.reserved {
  display: block;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.low {
  color: var(--color-warning);
  font-weight: 700;
}

.out {
  color: var(--color-ink-soft);
}
</style>
