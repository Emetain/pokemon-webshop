<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardFilters from '../../components/catalog/CardFilters.vue'
import CardGrid from '../../components/catalog/CardGrid.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import { findGame } from '../../data/games'
import { catalogApi } from '../../services/api'
import { cardCountLabel, filterQuery, pageFromQuery, seriesLabel } from '../../utils/catalog'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  game: { type: String, required: true },
  setCode: { type: String, required: true },
  sort: { type: String, default: '' },
  category: { type: String, default: '' },
  color: { type: String, default: '' },
  rarity: { type: String, default: '' },
  variant: { type: String, default: '' },
  priced: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()

const detail = ref(null)
const cards = ref(null)
const loadingSet = ref(true)
const loadingCards = ref(true)
const notFound = ref(false)
const error = ref('')

const game = computed(() => findGame(props.game))
const gameLabel = computed(() => (game.value ? t(game.value.labelKey) : ''))
const currentPage = computed(() => pageFromQuery(props.page))
const filters = computed(() => ({
  sort: props.sort,
  category: props.category,
  color: props.color,
  rarity: props.rarity,
  variant: props.variant,
  priced: props.priced === 'true',
}))

const hasFilters = computed(() => Object.entries(filters.value).some(([name, value]) => name !== 'sort' && value))

const breadcrumbs = computed(() => [
  { label: t('catalog.home'), to: '/' },
  { label: gameLabel.value, to: { name: 'sets', params: { game: props.game } } },
  { label: detail.value?.set.name ?? props.setCode },
])

function applyFilters(changes) {
  router.push({ query: filterQuery({ ...filters.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ sort: props.sort }) })
}

let latestSetRequest = 0
let latestCardsRequest = 0

async function loadSet() {
  const request = ++latestSetRequest
  loadingSet.value = true
  notFound.value = false
  error.value = ''
  try {
    const result = await catalogApi.set(props.game, props.setCode)
    if (request === latestSetRequest) detail.value = result
  } catch (exception) {
    if (request !== latestSetRequest) return
    detail.value = null
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestSetRequest) loadingSet.value = false
  }
}

async function loadCards() {
  const request = ++latestCardsRequest
  loadingCards.value = true
  error.value = ''
  try {
    const result = await catalogApi.cards(props.game, props.setCode, {
      ...filters.value,
      page: currentPage.value - 1,
    })
    if (request === latestCardsRequest) cards.value = result
  } catch (exception) {
    if (request !== latestCardsRequest) return
    cards.value = null
    // A set that doesn't exist already shows its message through loadSet.
    if (exception.status !== 404) error.value = exception.message
  } finally {
    if (request === latestCardsRequest) loadingCards.value = false
  }
}

function reload() {
  loadSet()
  loadCards()
}

watch(() => [props.game, props.setCode], loadSet, { immediate: true })
watch(() => [props.game, props.setCode, filters.value, currentPage.value], loadCards, { immediate: true })
</script>

<template>
  <div v-if="notFound || !game" class="container">
    <CatalogNotice :title="t('catalog.notFound.setTitle')" :text="t('catalog.notFound.text')">
      <router-link class="btn btn-primary" :to="{ name: 'sets', params: { game: game ? props.game : 'pokemon' } }">
        {{ t('nav.allSets') }}
      </router-link>
    </CatalogNotice>
  </div>

  <div v-else :data-game="game.accent">
    <CatalogBand :accent="game.accent">
      <BreadcrumbTrail :items="breadcrumbs" />
      <div class="heading">
        <div class="heading-text">
          <h1 class="title">{{ detail?.set.name ?? setCode }}</h1>
          <p v-if="detail" class="facts">
            <span>{{ seriesLabel(detail.set.series) }}</span>
            <span v-if="detail.set.releaseDate">{{ formatDate(detail.set.releaseDate) }}</span>
            <span>{{ cardCountLabel(detail.set.cardCount) }}</span>
          </p>
        </div>
        <span v-if="detail?.set.logoUrl" class="logo-plate">
          <img class="logo" :src="detail.set.logoUrl" alt="" decoding="async" />
        </span>
      </div>
    </CatalogBand>

    <div class="container page">
      <CatalogNotice v-if="error" :title="error">
        <button class="btn btn-secondary" type="button" @click="reload">{{ t('catalog.retry') }}</button>
      </CatalogNotice>

      <template v-else>
        <CardFilters
          :game="props.game"
          :values="filters"
          :options="detail ?? {}"
          @change="applyFilters"
          @clear="clearFilters"
        />
        <p v-if="cards && !loadingCards" class="count" role="status">{{ cardCountLabel(cards.totalItems) }}</p>

        <CardGrid v-if="loadingCards || loadingSet" loading />

        <CatalogNotice
          v-else-if="!cards?.items.length && hasFilters"
          :title="t('catalog.empty.filterTitle')"
          :text="t('catalog.empty.filterText')"
        >
          <button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('catalog.filters.clear') }}</button>
        </CatalogNotice>

        <CatalogNotice
          v-else-if="!cards?.items.length"
          :title="t('catalog.empty.setTitle')"
          :text="t('catalog.empty.setText')"
        />

        <template v-else>
          <CardGrid :cards="cards.items" />
          <PageLinks :page="currentPage" :total-pages="cards.totalPages" />
        </template>
      </template>
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

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-4);
  margin-top: var(--space-1);
  font-size: var(--text-sm);
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

.page {
  padding-block: var(--space-4) 0;
}

.count {
  margin-block: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

@media (min-width: 640px) {
  .logo-plate {
    display: block;
  }
}
</style>
