<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardFilters from '../../components/catalog/CardFilters.vue'
import CardGrid from '../../components/catalog/CardGrid.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import { pokedexApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { cardCountLabel, dexLabel, filterQuery, pageFromQuery } from '../../utils/catalog'
import { formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  dexNumber: { type: String, required: true },
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

const entry = ref(null)
const cards = ref(null)
const loadingEntry = ref(true)
const loadingCards = ref(true)
const notFound = ref(false)
const error = ref('')

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
  { label: t('pokedex.title'), to: '/collectie/pokedex' },
  { label: entry.value?.name ?? dexLabel(props.dexNumber) },
])

const ownedText = computed(() => {
  if (!entry.value) return ''
  const total = formatNumber(entry.value.totalCards)
  return entry.value.ownedCards === null
    ? t('pokedex.entry.total', { total })
    : t('pokedex.entry.ownedCount', { owned: formatNumber(entry.value.ownedCards), total })
})

const neighbourLink = (neighbour) => ({ name: 'pokedex-entry', params: { dexNumber: neighbour.dexNumber } })

function applyFilters(changes) {
  router.push({ query: filterQuery({ ...filters.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ sort: props.sort }) })
}

let latestEntryRequest = 0
let latestCardsRequest = 0

async function loadEntry() {
  const request = ++latestEntryRequest
  loadingEntry.value = true
  notFound.value = false
  error.value = ''
  try {
    const result = await pokedexApi.entry(props.dexNumber)
    if (request === latestEntryRequest) entry.value = result
  } catch (exception) {
    if (request !== latestEntryRequest) return
    entry.value = null
    // A number that isn't a number is a 400, and just as much a Pokémon that doesn't exist.
    if (exception.status === 404 || exception.status === 400) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestEntryRequest) loadingEntry.value = false
  }
}

async function loadCards() {
  const request = ++latestCardsRequest
  loadingCards.value = true
  try {
    const result = await pokedexApi.cards(props.dexNumber, { ...filters.value, page: currentPage.value - 1 })
    if (request === latestCardsRequest) cards.value = result
  } catch (exception) {
    if (request !== latestCardsRequest) return
    cards.value = null
    // A Pokémon that doesn't exist already shows its message through loadEntry.
    if (exception.status !== 404 && exception.status !== 400) error.value = exception.message
  } finally {
    if (request === latestCardsRequest) loadingCards.value = false
  }
}

function reload() {
  loadEntry()
  loadCards()
}

function onCollectionChange(card, change) {
  const wasOwned = card.owned.length > 0
  card.owned = change.entries
  const isOwned = card.owned.length > 0
  if (entry.value?.ownedCards !== null && wasOwned !== isOwned) entry.value.ownedCards += isOwned ? 1 : -1
}

watch(() => props.dexNumber, loadEntry, { immediate: true })
watch(() => [props.dexNumber, filters.value, currentPage.value], loadCards, { immediate: true })
</script>

<template>
  <div v-if="notFound" class="container">
    <CatalogNotice :title="t('pokedex.entry.notFoundTitle')" :text="t('catalog.notFound.text')">
      <router-link class="btn btn-primary" to="/collectie/pokedex">{{ t('pokedex.title') }}</router-link>
    </CatalogNotice>
  </div>

  <div v-else data-game="pokemon">
    <CatalogBand accent="pokemon">
      <BreadcrumbTrail :items="breadcrumbs" />
      <h1 class="title">{{ dexLabel(dexNumber) }} {{ entry?.name }}</h1>
      <p class="intro">{{ ownedText }}</p>
      <nav v-if="entry" class="neighbours">
        <router-link
          v-if="entry.previous"
          class="neighbour"
          :to="neighbourLink(entry.previous)"
          :aria-label="t('pokedex.entry.previous', { name: entry.previous.name })"
        >
          <ChevronLeft :size="18" aria-hidden="true" />
          {{ entry.previous.name }}
        </router-link>
        <router-link
          v-if="entry.next"
          class="neighbour next"
          :to="neighbourLink(entry.next)"
          :aria-label="t('pokedex.entry.next', { name: entry.next.name })"
        >
          {{ entry.next.name }}
          <ChevronRight :size="18" aria-hidden="true" />
        </router-link>
      </nav>
    </CatalogBand>

    <div class="container page">
      <CatalogNotice v-if="error" :title="error">
        <button class="btn btn-secondary" type="button" @click="reload">{{ t('catalog.retry') }}</button>
      </CatalogNotice>

      <template v-else>
        <CardFilters
          game="pokemon"
          :values="filters"
          :options="entry ?? {}"
          default-sort="price-high"
          :tracking="session.isLoggedIn.value"
          @change="applyFilters"
          @clear="clearFilters"
        />
        <p v-if="cards && !loadingCards" class="count" role="status">{{ cardCountLabel(cards.totalItems) }}</p>

        <CardGrid v-if="loadingCards || loadingEntry" loading />

        <CatalogNotice
          v-else-if="!cards?.items.length"
          :title="t('pokedex.entry.emptyTitle')"
          :text="hasFilters ? t('pokedex.entry.emptyFilterText') : t('pokedex.entry.emptyText')"
        >
          <button v-if="hasFilters" class="btn btn-secondary" type="button" @click="clearFilters">
            {{ t('catalog.filters.clear') }}
          </button>
        </CatalogNotice>

        <template v-else>
          <CardGrid :cards="cards.items" show-set @change="onCollectionChange" />
          <PageLinks :page="currentPage" :total-pages="cards.totalPages" />
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.title {
  margin-top: var(--space-3);
}

.intro {
  margin-top: var(--space-1);
}

.neighbours {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.neighbour {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  color: var(--accent-ink);
  font-size: var(--text-sm);
  font-weight: 600;
}

.neighbour.next {
  margin-left: auto;
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
