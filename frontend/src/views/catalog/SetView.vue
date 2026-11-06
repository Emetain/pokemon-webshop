<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardFilters from '../../components/catalog/CardFilters.vue'
import CardGrid from '../../components/catalog/CardGrid.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import SetProgressPanel from '../../components/collection/SetProgressPanel.vue'
import FormMessage from '../../components/FormMessage.vue'
import { findGame } from '../../data/games'
import { catalogApi, collectionApi } from '../../services/api'
import { useSession } from '../../stores/session'
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
  collection: { type: String, default: '' },
  master: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const session = useSession()

const detail = ref(null)
const cards = ref(null)
const loadingSet = ref(true)
const loadingCards = ref(true)
const notFound = ref(false)
const error = ref('')
const stats = ref(null)
const addingPage = ref(false)
const pageMessage = ref('')

const tracking = computed(() => session.isLoggedIn.value)
const masterMode = computed(() => props.master === 'true')

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
  collection: props.collection,
}))

const hasFilters = computed(() => Object.entries(filters.value).some(([name, value]) => name !== 'sort' && value))

const breadcrumbs = computed(() => [
  { label: t('catalog.home'), to: '/' },
  { label: gameLabel.value, to: { name: 'sets', params: { game: props.game } } },
  { label: detail.value?.set.name ?? props.setCode },
])

function applyFilters(changes) {
  router.push({ query: filterQuery({ ...filters.value, master: masterMode.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ sort: props.sort, master: masterMode.value }) })
}

function setMode(master) {
  applyFilters({ master })
}

const missingLink = computed(() => ({
  name: 'set-missing',
  params: { game: props.game, setCode: props.setCode },
  query: filterQuery({ master: masterMode.value }),
}))

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
      master: masterMode.value || undefined,
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

let latestStatsRequest = 0

async function loadStats() {
  if (!tracking.value) return
  const request = ++latestStatsRequest
  try {
    const result = await collectionApi.set(props.game, props.setCode)
    if (request === latestStatsRequest) stats.value = result
  } catch {
    // The cards still work without the numbers above them.
    if (request === latestStatsRequest) stats.value = null
  }
}

function reload() {
  loadSet()
  loadCards()
  loadStats()
}

function onCollectionChange(card, change) {
  card.owned = change.entries
  stats.value = change.progress
}

async function addPage() {
  addingPage.value = true
  pageMessage.value = ''
  try {
    const { added } = await collectionApi.addBulk(cards.value.items.map((card) => card.id))
    await Promise.all([loadCards(), loadStats()])
    if (added === 0) pageMessage.value = t('collection.set.addedNothing')
    else if (added === 1) pageMessage.value = t('collection.set.addedPageOne')
    else pageMessage.value = t('collection.set.addedPage', { count: added })
  } catch (exception) {
    error.value = exception.message
  } finally {
    addingPage.value = false
  }
}

watch(() => [props.game, props.setCode], loadSet, { immediate: true })
watch(() => [props.game, props.setCode], loadStats, { immediate: true })
watch(
  () => [props.game, props.setCode, filters.value, masterMode.value, currentPage.value],
  () => {
    pageMessage.value = ''
    loadCards()
  },
  { immediate: true },
)
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
        <SetProgressPanel
          v-if="stats"
          :stats="stats"
          :master="masterMode"
          :set-name="detail?.set.name ?? setCode"
          :missing-link="missingLink"
          @mode="setMode"
        />
        <CardFilters
          :game="props.game"
          :values="filters"
          :options="detail ?? {}"
          :tracking="tracking"
          @change="applyFilters"
          @clear="clearFilters"
        />
        <div class="list-head">
          <p v-if="cards && !loadingCards" class="count" role="status">{{ cardCountLabel(cards.totalItems) }}</p>
          <button
            v-if="tracking && cards?.items.length && !loadingCards"
            class="add-page"
            type="button"
            :disabled="addingPage"
            @click="addPage"
          >
            {{ t('collection.set.addPage') }}
          </button>
        </div>
        <FormMessage v-if="pageMessage" type="success" class="page-message">{{ pageMessage }}</FormMessage>

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
          <CardGrid :cards="cards.items" :master="masterMode" @change="onCollectionChange" />
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

.list-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0 var(--space-3);
  min-height: var(--touch-target);
  margin-block: var(--space-2);
}

.count {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.add-page {
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.add-page:hover:not(:disabled) {
  text-decoration: underline;
}

.add-page:disabled {
  cursor: progress;
  opacity: 0.6;
}

.page-message {
  margin-bottom: var(--space-3);
}

@media (min-width: 640px) {
  .logo-plate {
    display: block;
  }
}
</style>
