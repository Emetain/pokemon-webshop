<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardFilters from '../../components/catalog/CardFilters.vue'
import CardGrid from '../../components/catalog/CardGrid.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import GuestInvite from '../../components/collection/GuestInvite.vue'
import ProductGrid from '../../components/shop/ProductGrid.vue'
import { productWishlistApi, wishlistApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { cardCountLabel, filterQuery, pageFromQuery } from '../../utils/catalog'
import { t } from '../../utils/i18n'

const props = defineProps({
  sort: { type: String, default: '' },
  priced: { type: String, default: '' },
  collection: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const session = useSession()

const cards = ref(null)
const products = ref(null)
const productError = ref('')
const loading = ref(true)
const error = ref('')

const loggedIn = computed(() => session.isLoggedIn.value)
const currentPage = computed(() => pageFromQuery(props.page))
const filters = computed(() => ({
  sort: props.sort,
  priced: props.priced === 'true',
  collection: props.collection,
}))

const hasFilters = computed(() => filters.value.priced || Boolean(filters.value.collection))

function applyFilters(changes) {
  router.push({ query: filterQuery({ ...filters.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ sort: props.sort }) })
}

let latestRequest = 0

async function load() {
  if (!loggedIn.value) return
  const request = ++latestRequest
  loading.value = true
  error.value = ''
  try {
    const result = await wishlistApi.cards({ ...filters.value, page: currentPage.value - 1 })
    if (request === latestRequest) cards.value = result
  } catch (exception) {
    if (request !== latestRequest) return
    cards.value = null
    error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

async function remove(card) {
  error.value = ''
  try {
    await wishlistApi.remove(card.id)
    await load()
  } catch (exception) {
    error.value = exception.message
  }
}

function onCollectionChange(card, change) {
  card.owned = change.entries
}

const PRODUCTS_SHOWN = 48

/** Products are a short list, so they come in one go above the cards and don't follow the card filters. */
async function loadProducts() {
  if (!loggedIn.value) return
  productError.value = ''
  try {
    products.value = await productWishlistApi.products({ size: PRODUCTS_SHOWN })
  } catch (exception) {
    productError.value = exception.message
  }
}

function onProductWished(product, wished) {
  if (!wished) products.value.items = products.value.items.filter((item) => item.id !== product.id)
}

loadProducts()
watch(() => [filters.value, currentPage.value], load, { immediate: true })
</script>

<template>
  <CatalogBand accent="pokemon">
    <BreadcrumbTrail :items="[{ label: t('catalog.home'), to: '/' }, { label: t('wishlist.title') }]" />
    <h1 class="title">{{ t('wishlist.title') }}</h1>
    <p class="intro">{{ t('wishlist.intro') }}</p>
  </CatalogBand>

  <div class="container page">
    <GuestInvite v-if="!loggedIn" :title="t('wishlist.guestTitle')" :text="t('wishlist.guestText')" />

    <CatalogNotice v-else-if="error" :title="error">
      <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
    </CatalogNotice>

    <template v-else>
      <section class="products" aria-labelledby="wishlist-products">
        <h2 id="wishlist-products">{{ t('wishlist.products') }}</h2>
        <CatalogNotice v-if="productError" :title="productError" />
        <p v-else-if="products && !products.items.length" class="products-empty">
          {{ t('wishlist.productsEmpty') }}
          <router-link to="/producten">{{ t('wishlist.productsAction') }}</router-link>
        </p>
        <ProductGrid
          v-else-if="products"
          :products="products.items"
          @wished="onProductWished"
          @error="productError = $event"
        />
      </section>

      <h2 class="cards-title">{{ t('wishlist.cards') }}</h2>
      <CardFilters
        :values="filters"
        default-sort="price-high"
        tracking
        @change="applyFilters"
        @clear="clearFilters"
      />
      <p v-if="cards && !loading" class="count" role="status">{{ cardCountLabel(cards.totalItems) }}</p>

      <CardGrid v-if="loading" loading />

      <CatalogNotice
        v-else-if="!cards?.items.length && hasFilters"
        :title="t('catalog.empty.filterTitle')"
        :text="t('catalog.empty.filterText')"
      >
        <button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('catalog.filters.clear') }}</button>
      </CatalogNotice>

      <CatalogNotice
        v-else-if="!cards?.items.length"
        :title="t('wishlist.emptyTitle')"
        :text="t('wishlist.emptyText')"
      >
        <router-link class="btn btn-primary" :to="{ name: 'sets', params: { game: 'pokemon' } }">
          {{ t('wishlist.emptyAction') }}
        </router-link>
      </CatalogNotice>

      <template v-else>
        <CardGrid :cards="cards.items" show-set @change="onCollectionChange">
          <template #actions="{ card }">
            <button
              class="remove"
              type="button"
              :aria-label="t('wishlist.removeLabel', { name: card.name })"
              @click="remove(card)"
            >
              {{ t('wishlist.remove') }}
            </button>
          </template>
        </CardGrid>
        <PageLinks :page="currentPage" :total-pages="cards.totalPages" />
      </template>
    </template>
  </div>
</template>

<style scoped>
.products {
  display: grid;
  gap: var(--space-4);
  margin-bottom: var(--space-7);
}

.products-empty {
  color: var(--color-ink-soft);
}

.cards-title {
  margin-bottom: var(--space-4);
}

.title {
  margin-top: var(--space-3);
}

.intro {
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

.remove {
  align-self: flex-start;
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-danger);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.remove:hover {
  text-decoration: underline;
}
</style>
