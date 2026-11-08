<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarClock, CircleCheck, CircleX, ShoppingBag } from 'lucide-vue-next'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import FormMessage from '../../components/FormMessage.vue'
import PriceTag from '../../components/shop/PriceTag.vue'
import ProductBadges from '../../components/shop/ProductBadges.vue'
import ProductImage from '../../components/shop/ProductImage.vue'
import ProductRow from '../../components/shop/ProductRow.vue'
import StockAlertForm from '../../components/shop/StockAlertForm.vue'
import WishlistToggle from '../../components/shop/WishlistToggle.vue'
import { findGame } from '../../data/games'
import {
  accessoryLabel,
  conditionLabel,
  languageLabel,
  shopPath,
  typeLabel,
  typePluralLabel,
} from '../../data/shop'
import { shopApi } from '../../services/api'
import { useCurrency } from '../../stores/currency'
import { variantLabel } from '../../utils/catalog'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  slug: { type: String, required: true },
})

const { formatPrice } = useCurrency()

const detail = ref(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')
const message = ref('')
const shown = ref(0)

const product = computed(() => detail.value?.product)
const game = computed(() => (product.value?.game ? findGame(product.value.game) : null))

/** Own photos first. A single without photos shows its card, which is never changed or covered. */
const gallery = computed(() => {
  const images = detail.value?.images ?? []
  if (images.length) return images.map((src) => ({ src, isCard: false }))
  if (detail.value?.card?.imageUrl) return [{ src: detail.value.card.imageUrl, isCard: true }]
  return []
})

const breadcrumbs = computed(() => {
  const items = [{ label: t('catalog.home'), to: '/' }]
  const p = product.value
  if (p.type === 'ACCESSORY') items.push({ label: t('shop.titles.accessories'), to: '/accessoires' })
  else if (game.value) {
    items.push({ label: t(game.value.labelKey), to: shopPath(p.game) })
    items.push({ label: typePluralLabel(p.type), to: shopPath(p.game, { type: p.type }) })
  }
  items.push({ label: p.name })
  return items
})

const facts = computed(() => {
  const p = product.value
  const list = [
    { label: t('shop.product.type'), value: p.accessoryType ? accessoryLabel(p.accessoryType) : typeLabel(p.type) },
  ]
  if (p.language) list.push({ label: t('shop.product.language'), value: languageLabel(p.language) })
  if (detail.value.set) {
    list.push({
      label: t('shop.product.set'),
      value: detail.value.set.name,
      to: { name: 'set', params: { game: detail.value.set.game, setCode: detail.value.set.code } },
    })
  }
  if (p.condition) list.push({ label: t('shop.product.condition'), value: conditionLabel(p.condition) })
  if (detail.value.cardVariant) list.push({ label: t('shop.product.variant'), value: variantLabel(detail.value.cardVariant) })
  if (p.gradingCompany) list.push({ label: t('shop.product.grade'), value: `${p.gradingCompany} ${p.grade}` })
  if (p.releaseDate) list.push({ label: t('shop.product.release'), value: formatDate(p.releaseDate) })
  if (p.preOrder && p.shipsFrom) list.push({ label: t('shop.product.shipsFrom'), value: formatDate(p.shipsFrom) })
  return list
})

const stockLine = computed(() => {
  const p = product.value
  if (p.preOrder && p.canBuy) {
    return {
      kind: 'pre-order',
      text: p.shipsFrom ? t('shop.stock.preOrder', { date: formatDate(p.shipsFrom) }) : t('shop.stock.preOrderNoDate'),
    }
  }
  if (p.canBuy && p.lastFew) return { kind: 'last-few', text: t('shop.stock.lastFew', { count: p.lastFew }) }
  if (p.canBuy) return { kind: 'in-stock', text: t('shop.stock.inStock') }
  return { kind: 'sold-out', text: t('shop.stock.soldOut') }
})

/** The market price of the variant this single is, or the first one with a price. */
const marketPrice = computed(() => {
  const prices = detail.value?.card?.prices ?? []
  const withTrend = prices.filter((price) => price.trendCents !== null)
  return withTrend.find((price) => price.variant === detail.value.cardVariant) ?? withTrend[0] ?? null
})

let latestRequest = 0

async function load() {
  const request = ++latestRequest
  loading.value = true
  notFound.value = false
  error.value = ''
  shown.value = 0
  try {
    const result = await shopApi.product(props.slug)
    if (request === latestRequest) detail.value = result
  } catch (exception) {
    if (request !== latestRequest) return
    detail.value = null
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

function onWished(item, wished) {
  item.wished = wished
}

watch(() => props.slug, load, { immediate: true })
</script>

<template>
  <div v-if="notFound" class="container">
    <CatalogNotice :title="t('shop.notFound.productTitle')" :text="t('shop.notFound.text')">
      <router-link class="btn btn-primary" to="/producten">{{ t('shop.titles.all') }}</router-link>
    </CatalogNotice>
  </div>

  <div v-else-if="error" class="container">
    <CatalogNotice :title="error">
      <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
    </CatalogNotice>
  </div>

  <div v-else-if="loading" class="container page" aria-busy="true">
    <div class="skeleton"></div>
  </div>

  <div v-else :data-game="game?.accent" class="container page">
    <BreadcrumbTrail class="trail" :items="breadcrumbs" />
    <FormMessage v-if="detail.status !== 'ACTIVE'" class="draft">{{ t('shop.product.draft') }}</FormMessage>

    <div class="top">
      <section class="gallery" :aria-label="t('shop.product.images', { name: product.name })">
        <div class="main-image">
          <CardImage v-if="gallery[shown]?.isCard" class="main-card" :src="gallery[shown].src" :alt="product.name" eager />
          <ProductImage
            v-else
            :src="gallery[shown]?.src"
            :alt="product.name"
            :set-logo="product.setLogoUrl"
            :type="product.type"
            :game="game?.accent"
            eager
          />
        </div>
        <ul v-if="gallery.length > 1" class="thumbs">
          <li v-for="(image, index) in gallery" :key="image.src">
            <button
              class="thumb"
              type="button"
              :aria-label="t('shop.product.showImage', { number: index + 1 })"
              :aria-current="shown === index ? 'true' : undefined"
              @click="shown = index"
            >
              <img :src="image.src" alt="" loading="lazy" decoding="async" />
            </button>
          </li>
        </ul>
      </section>

      <section class="buy">
        <ProductBadges :product="product" />
        <h1 class="name">{{ product.name }}</h1>
        <PriceTag :product="product" large />

        <p class="stock" :class="stockLine.kind">
          <CalendarClock v-if="stockLine.kind === 'pre-order'" :size="18" aria-hidden="true" />
          <CircleX v-else-if="stockLine.kind === 'sold-out'" :size="18" aria-hidden="true" />
          <CircleCheck v-else :size="18" aria-hidden="true" />
          {{ stockLine.text }}
        </p>

        <div class="actions">
          <button class="btn btn-primary add" type="button" disabled>
            <ShoppingBag :size="20" aria-hidden="true" />
            {{ t('shop.addToCart') }}
          </button>
          <WishlistToggle :product="product" with-text @change="product.wished = $event" @error="message = $event" />
        </div>
        <p v-if="product.canBuy" class="soon">{{ t('shop.cartSoon') }}</p>
        <FormMessage v-if="message">{{ message }}</FormMessage>

        <StockAlertForm v-if="!product.canBuy" :slug="product.slug" />

        <dl class="facts">
          <div v-for="fact in facts" :key="fact.label" class="fact">
            <dt>{{ fact.label }}</dt>
            <dd>
              <router-link v-if="fact.to" :to="fact.to">{{ fact.value }}</router-link>
              <template v-else>{{ fact.value }}</template>
            </dd>
          </div>
        </dl>
      </section>
    </div>

    <div class="more">
      <section v-if="detail.description" class="description">
        <h2>{{ t('shop.product.description') }}</h2>
        <p class="description-text">{{ detail.description }}</p>
      </section>

      <section v-if="detail.card" class="card-box">
        <h2>{{ t('shop.product.card') }}</h2>
        <div class="card-row">
          <CardImage class="card-thumb" :src="detail.card.imageUrl" :alt="detail.card.name" />
          <div class="card-text">
            <router-link
              class="card-link"
              :to="{ name: 'card', params: { game: detail.card.game, setCode: detail.card.setCode, cardId: detail.card.id } }"
            >
              {{ t('shop.product.cardLink', { name: detail.card.name }) }}
            </router-link>
            <p class="card-meta">{{ detail.card.setName }}, #{{ detail.card.number }}</p>
            <h3 class="market-title">{{ t('shop.product.marketPrice') }}</h3>
            <template v-if="marketPrice">
              <p class="market-price">
                {{ formatPrice(marketPrice.trendCents, marketPrice.currency) }}
                <span class="market-variant">{{ variantLabel(marketPrice.variant) }}</span>
              </p>
              <p class="market-note">
                {{ t('shop.product.marketNote', { source: t(`catalog.priceSources.${marketPrice.source}`) }) }}
                {{ t('catalog.card.source', { source: t(`catalog.priceSources.${marketPrice.source}`), date: formatDate(marketPrice.updatedAt) }) }}
              </p>
            </template>
            <p v-else class="market-note">{{ t('shop.product.marketNone') }}</p>
          </div>
        </div>
      </section>
    </div>

    <ProductRow
      v-if="detail.otherOffers.length"
      class="row"
      :title="t('shop.product.otherOffers')"
      :products="detail.otherOffers"
      @wished="onWished"
    />
    <ProductRow
      v-if="detail.related.length"
      class="row"
      :title="detail.set ? t('shop.product.related') : t('shop.product.relatedOther')"
      :products="detail.related"
      @wished="onWished"
    />
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-4) 0;
}

.skeleton {
  aspect-ratio: 1;
  max-width: 32rem;
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.trail {
  color: var(--color-ink-soft);
}

.draft {
  margin-top: var(--space-3);
}

.top {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-4);
}

.gallery {
  min-width: 0;
}

.main-image {
  max-width: 34rem;
}

.main-card {
  max-width: 22rem;
  margin-inline: auto;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.thumb {
  width: 4.5rem;
  height: 4.5rem;
  padding: var(--space-1);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  cursor: pointer;
}

.thumb[aria-current='true'] {
  border-color: var(--color-brand);
  box-shadow: inset 0 0 0 1px var(--color-brand);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.buy {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
}

.name {
  font-size: clamp(1.625rem, 4vw, 2.25rem);
  overflow-wrap: anywhere;
}

.stock {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 650;
}

.stock.in-stock {
  color: var(--color-success);
}

.stock.last-few {
  color: var(--color-warning);
}

.stock.pre-order {
  color: var(--color-brand-deep);
}

.stock.sold-out {
  color: var(--color-ink-soft);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.add {
  flex: 1 1 14rem;
}

.soon {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.facts {
  display: grid;
  margin: var(--space-2) 0 0;
  border-top: 1px solid var(--color-line);
}

.fact {
  display: grid;
  grid-template-columns: minmax(0, 9rem) minmax(0, 1fr);
  gap: var(--space-3);
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.fact dt {
  color: var(--color-ink-soft);
}

.fact dd {
  margin: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.more {
  display: grid;
  gap: var(--space-6);
  margin-top: var(--space-7);
}

.description-text {
  max-width: 70ch;
  margin-top: var(--space-3);
  white-space: pre-line;
}

.card-row {
  display: grid;
  grid-template-columns: 6rem minmax(0, 1fr);
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.card-link {
  font-weight: 650;
}

.card-meta {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.market-title {
  margin-top: var(--space-3);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 700;
}

.market-price {
  margin-top: var(--space-1);
  font-size: var(--text-lg);
  font-weight: 750;
}

.market-variant {
  margin-left: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 500;
}

.market-note {
  max-width: 60ch;
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.row {
  margin-top: var(--space-7);
}

@media (min-width: 900px) {
  .top {
    grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
    gap: var(--space-7);
  }

  .more {
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    align-items: start;
  }
}
</style>
