<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardFilters from '../../components/catalog/CardFilters.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import { findGame } from '../../data/games'
import { catalogApi } from '../../services/api'
import { useCurrency } from '../../stores/currency'
import { cardCountLabel, filterQuery, pageFromQuery } from '../../utils/catalog'
import { t } from '../../utils/i18n'

const PAGE_SIZE = 100

const props = defineProps({
  game: { type: String, required: true },
  setCode: { type: String, required: true },
  sort: { type: String, default: '' },
  category: { type: String, default: '' },
  color: { type: String, default: '' },
  rarity: { type: String, default: '' },
  variant: { type: String, default: '' },
  priced: { type: String, default: '' },
  master: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const { formatPrice } = useCurrency()

const detail = ref(null)
const cards = ref(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')

const game = computed(() => findGame(props.game))
const gameLabel = computed(() => (game.value ? t(game.value.labelKey) : ''))
const setName = computed(() => detail.value?.set.name ?? props.setCode)
const masterMode = computed(() => props.master === 'true')
const currentPage = computed(() => pageFromQuery(props.page))
const setLink = computed(() => ({
  name: 'set',
  params: { game: props.game, setCode: props.setCode },
  query: filterQuery({ master: masterMode.value }),
}))

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
  { label: setName.value, to: setLink.value },
  { label: t('collection.missingList.title') },
])

function applyFilters(changes) {
  router.push({ query: filterQuery({ ...filters.value, master: masterMode.value, ...changes }) })
}

function clearFilters() {
  router.push({ query: filterQuery({ sort: props.sort, master: masterMode.value }) })
}

let latestRequest = 0

async function load() {
  const request = ++latestRequest
  loading.value = true
  notFound.value = false
  error.value = ''
  try {
    const [set, result] = await Promise.all([
      catalogApi.set(props.game, props.setCode),
      catalogApi.cards(props.game, props.setCode, {
        ...filters.value,
        collection: 'missing',
        master: masterMode.value || undefined,
        size: PAGE_SIZE,
        page: currentPage.value - 1,
      }),
    ])
    if (request !== latestRequest) return
    detail.value = set
    cards.value = result
  } catch (exception) {
    if (request !== latestRequest) return
    cards.value = null
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

watch(() => [props.game, props.setCode, filters.value, masterMode.value, currentPage.value], load, { immediate: true })
</script>

<template>
  <div v-if="notFound || !game" class="container">
    <CatalogNotice :title="t('catalog.notFound.setTitle')" :text="t('catalog.notFound.text')" />
  </div>

  <div v-else :data-game="game.accent">
    <CatalogBand :accent="game.accent">
      <BreadcrumbTrail :items="breadcrumbs" />
      <h1 class="title">{{ t('collection.missingList.title') }}</h1>
      <p class="intro">
        {{
          masterMode
            ? t('collection.missingList.introMaster', { set: setName })
            : t('collection.missingList.intro', { set: setName })
        }}
      </p>
    </CatalogBand>

    <div class="container page">
      <CatalogNotice v-if="error" :title="error">
        <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
      </CatalogNotice>

      <template v-else>
        <CardFilters
          :game="props.game"
          :values="filters"
          :options="detail ?? {}"
          @change="applyFilters"
          @clear="clearFilters"
        />
        <p v-if="cards && !loading" class="count" role="status">{{ cardCountLabel(cards.totalItems) }}</p>

        <p v-if="loading" class="loading" role="status">{{ t('catalog.loading') }}</p>

        <CatalogNotice
          v-else-if="!cards?.items.length && hasFilters"
          :title="t('catalog.empty.filterTitle')"
          :text="t('catalog.empty.filterText')"
        >
          <button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('catalog.filters.clear') }}</button>
        </CatalogNotice>

        <CatalogNotice
          v-else-if="!cards?.items.length"
          :title="t('collection.missingList.complete')"
          :text="t('collection.missingList.completeText')"
        >
          <router-link class="btn btn-secondary" :to="setLink">{{ t('collection.missingList.backToSet') }}</router-link>
        </CatalogNotice>

        <template v-else>
          <div class="table-scroll">
            <table class="list">
              <thead>
                <tr>
                  <th scope="col">{{ t('collection.missingList.number') }}</th>
                  <th scope="col">{{ t('collection.missingList.name') }}</th>
                  <th class="rarity" scope="col">{{ t('collection.missingList.rarity') }}</th>
                  <th class="amount" scope="col">{{ t('collection.missingList.price') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="card in cards.items" :key="card.id">
                  <td class="number">{{ card.number }}</td>
                  <th scope="row">
                    <router-link :to="{ name: 'card', params: { game: card.game, setCode: card.setCode, cardId: card.id } }">
                      {{ card.name }}
                    </router-link>
                  </th>
                  <td class="rarity">{{ card.rarity }}</td>
                  <td class="amount" :class="{ empty: card.trendCents === null }">
                    {{ card.trendCents === null ? t('catalog.noPrice') : formatPrice(card.trendCents, card.currency) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
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

.page {
  padding-block: var(--space-4) 0;
}

.count {
  margin-block: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.loading {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
}

.table-scroll {
  overflow-x: auto;
}

.list {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.list th,
.list td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-line);
  text-align: left;
}

.list thead th {
  border-bottom: 0;
  background: var(--color-tint);
  font-weight: 650;
}

.list tbody th {
  font-weight: 550;
}

.list .number {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.list .amount {
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.list td.amount {
  font-weight: 700;
}

.list td.empty {
  color: var(--color-ink-soft);
  font-weight: 400;
}

/* The rarity is the first thing to go on a phone: a card name needs the room. */
.rarity {
  display: none;
}

@media (min-width: 640px) {
  .list .rarity {
    display: table-cell;
  }
}
</style>
