<script setup>
import { computed, ref, watch } from 'vue'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import CardCollectionPanel from '../../components/collection/CardCollectionPanel.vue'
import { findGame } from '../../data/games'
import { catalogApi } from '../../services/api'
import { useCurrency } from '../../stores/currency'
import { categoryLabel, colorFilterLabel, colorLabel, dexLabel, variantLabel } from '../../utils/catalog'
import { productFacts } from '../../data/shop'
import { formatDate, formatMoney, formatRate } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  game: { type: String, required: true },
  setCode: { type: String, required: true },
  cardId: { type: String, required: true },
})

const { conversionRate, formatPrice } = useCurrency()

const card = ref(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')

const game = computed(() => findGame(card.value?.game ?? props.game))

const breadcrumbs = computed(() => [
  { label: t('catalog.home'), to: '/' },
  { label: t(game.value.labelKey), to: { name: 'sets', params: { game: card.value.game } } },
  { label: card.value.set.name, to: { name: 'set', params: { game: card.value.game, setCode: card.value.set.code } } },
  { label: card.value.name },
])

/** All prices of a card normally share a source and a date. If not, the oldest date is the honest one to show. */
const priceSource = computed(() => {
  const prices = card.value?.prices ?? []
  if (!prices.length) return ''
  const sources = [...new Set(prices.map((price) => t(`catalog.priceSources.${price.source}`)))]
  const oldest = prices.map((price) => price.updatedAt).sort()[0]
  return t('catalog.card.source', { source: sources.join(', '), date: formatDate(oldest) })
})

const conversionNote = computed(() => {
  const price = card.value?.prices[0]
  const rate = price && conversionRate(price.currency)
  if (!rate) return ''
  return t('catalog.card.converted', {
    date: formatDate(rate.date),
    rate: formatRate(rate.perEuro),
    currency: rate.currency,
  })
})

const priceText = (cents, currency) => (cents === null ? t('catalog.noPrice') : formatPrice(cents, currency))

let latestRequest = 0

async function load() {
  const request = ++latestRequest
  loading.value = true
  notFound.value = false
  error.value = ''
  try {
    const result = await catalogApi.card(props.cardId)
    if (request === latestRequest) card.value = result
  } catch (exception) {
    if (request !== latestRequest) return
    card.value = null
    // An id that isn't a number is a 400, and just as much a card that doesn't exist.
    if (exception.status === 404 || exception.status === 400) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

watch(() => props.cardId, load, { immediate: true })
</script>

<template>
  <div v-if="notFound" class="container">
    <CatalogNotice :title="t('catalog.notFound.cardTitle')" :text="t('catalog.notFound.text')">
      <router-link class="btn btn-primary" :to="{ name: 'sets', params: { game: game ? game.slug : 'pokemon' } }">
        {{ t('nav.allSets') }}
      </router-link>
    </CatalogNotice>
  </div>

  <div v-else-if="error" class="container">
    <CatalogNotice :title="error">
      <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
    </CatalogNotice>
  </div>

  <div v-else-if="loading" class="container page" :aria-label="t('catalog.loading')" aria-busy="true">
    <div class="layout">
      <div class="skeleton"></div>
    </div>
  </div>

  <article v-else class="container page" :data-game="game?.accent">
    <BreadcrumbTrail class="trail" :items="breadcrumbs" />

    <div class="layout">
      <CardImage class="image" :src="card.imageLargeUrl ?? card.imageSmallUrl" :alt="card.name" eager />

      <div class="info">
        <h1>{{ card.name }}</h1>

        <dl class="facts">
          <div class="fact">
            <dt>{{ t('catalog.card.set') }}</dt>
            <dd>
              <router-link :to="{ name: 'set', params: { game: card.game, setCode: card.set.code } }">
                {{ card.set.name }}
              </router-link>
            </dd>
          </div>
          <div class="fact">
            <dt>{{ t('catalog.card.number') }}</dt>
            <dd>{{ card.number }}</dd>
          </div>
          <div v-if="card.rarity" class="fact">
            <dt>{{ t('catalog.card.rarity') }}</dt>
            <dd>{{ card.rarity }}</dd>
          </div>
          <div v-if="card.cardType" class="fact">
            <dt>{{ t('catalog.card.type') }}</dt>
            <dd>{{ categoryLabel(card.cardType) }}</dd>
          </div>
          <div v-if="card.colors.length" class="fact">
            <dt>{{ colorFilterLabel(card.game) }}</dt>
            <dd>{{ card.colors.map(colorLabel).join(', ') }}</dd>
          </div>
          <div v-if="card.variants.length" class="fact">
            <dt>{{ t('catalog.card.variants') }}</dt>
            <dd>{{ card.variants.map(variantLabel).join(', ') }}</dd>
          </div>
          <div v-if="card.dexNumbers.length" class="fact">
            <dt>{{ t('catalog.card.pokedex') }}</dt>
            <dd class="dex-links">
              <router-link
                v-for="number in card.dexNumbers"
                :key="number"
                :to="{ name: 'pokedex-entry', params: { dexNumber: number } }"
              >
                {{ dexLabel(number) }}
              </router-link>
            </dd>
          </div>
        </dl>

        <section v-if="card.offers.length" class="offers">
          <h2>{{ t('shop.offersTitle') }}</h2>
          <ul class="offer-list">
            <li v-for="offer in card.offers" :key="offer.id" class="offer">
              <router-link class="offer-link" :to="{ name: 'product', params: { slug: offer.slug } }">
                <span class="offer-name">{{ productFacts(offer).filter((fact) => fact !== offer.setName).join(', ') }}</span>
                <span class="offer-price">{{ formatMoney(offer.shownPriceCents, 'EUR') }}</span>
              </router-link>
            </li>
          </ul>
        </section>

        <CardCollectionPanel :card="card" />

        <section class="prices">
          <h2>{{ t('catalog.card.prices') }}</h2>

          <template v-if="card.prices.length">
            <div class="table-scroll">
              <table class="price-table">
                <thead>
                  <tr>
                    <th scope="col">{{ t('catalog.card.variant') }}</th>
                    <th class="amount" scope="col">{{ t('catalog.card.trend') }}</th>
                    <th class="amount" scope="col">{{ t('catalog.card.average') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="price in card.prices" :key="price.variant">
                    <th scope="row">{{ variantLabel(price.variant) }}</th>
                    <td class="amount" :class="{ empty: price.trendCents === null }">{{ priceText(price.trendCents, price.currency) }}</td>
                    <td class="amount" :class="{ empty: price.averageCents === null }">
                      {{ priceText(price.averageCents, price.currency) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="source">{{ priceSource }}</p>
            <p v-if="conversionNote" class="source">{{ conversionNote }}</p>
          </template>

          <p v-else class="source">{{ t('catalog.card.noPrices') }}</p>
        </section>
      </div>
    </div>
  </article>
</template>

<style scoped>
.offers {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.offers h2 {
  font-size: var(--text-lg);
}

.offer-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.offer-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: var(--touch-target);
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--color-line);
  color: var(--color-ink);
  font-size: var(--text-sm);
  text-decoration: none;
}

.offer:last-child .offer-link {
  border-bottom: 1px solid var(--color-line);
}

.offer-link:hover .offer-name {
  color: var(--color-brand);
  text-decoration: underline;
}

.offer-price {
  flex: none;
  color: var(--color-success);
  font-weight: 750;
}

.page {
  padding-block: var(--space-4) 0;
}

.trail {
  margin-bottom: var(--space-4);
  color: var(--color-ink-soft);
}

.layout {
  display: grid;
  gap: var(--space-5);
}

.image,
.skeleton {
  width: 100%;
  max-width: 24rem;
  margin-inline: auto;
}

.skeleton {
  aspect-ratio: 63 / 88;
  border-radius: var(--radius-card);
  background: var(--color-tint);
}

.info {
  min-width: 0;
}

.facts {
  margin: var(--space-4) 0 0;
}

.fact {
  display: grid;
  grid-template-columns: 8rem minmax(0, 1fr);
  gap: var(--space-2);
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.fact dt {
  color: var(--color-ink-soft);
}

.fact dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.dex-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-3);
}

.prices {
  margin-top: var(--space-5);
}

.table-scroll {
  margin-top: var(--space-3);
  overflow-x: auto;
}

.price-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.price-table th,
.price-table td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-line);
  text-align: left;
}

.price-table thead th {
  background: var(--color-tint);
  border-bottom: 0;
  font-weight: 650;
}

.price-table tbody th {
  font-weight: 550;
}

.price-table .amount {
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.price-table td.amount {
  font-weight: 700;
}

.price-table td.empty {
  color: var(--color-ink-soft);
  font-weight: 400;
}

.source {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

@media (min-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 26rem) minmax(0, 1fr);
    gap: var(--space-7);
    align-items: start;
  }

  .image,
  .skeleton {
    max-width: none;
  }
}
</style>
