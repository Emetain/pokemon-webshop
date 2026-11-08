<script setup>
import { computed, reactive, ref, useId } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import CardPicker from '../../components/admin/CardPicker.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { CONDITIONS, LANGUAGES, conditionLabel, eurosToCents, languageLabel } from '../../data/shop'
import { adminShopApi } from '../../services/api'
import { statusLabel } from '../../utils/admin'
import { variantLabel } from '../../utils/catalog'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * A single in three steps: find the card, look at its market price, set condition, price and stock. The market
 * price is a suggestion and is never filled in as the shop price.
 */
const uid = useId()
const field = (name) => `${uid}-${name}`

const game = ref('pokemon')
const suggestion = ref(null)
const created = ref(null)
const busy = ref(false)
const error = ref('')

const form = reactive({
  cardVariant: '',
  condition: 'NEAR_MINT',
  language: 'EN',
  price: '',
  quantity: '1',
  status: 'ACTIVE',
})

const marketOfVariant = computed(() =>
  suggestion.value?.prices.find((price) => price.variant === form.cardVariant && price.trendEuroCents !== null),
)

async function choose(card) {
  error.value = ''
  created.value = null
  try {
    suggestion.value = await adminShopApi.market(card.id)
    form.cardVariant = suggestion.value.card.variants[0] ?? ''
    form.price = ''
  } catch (exception) {
    error.value = exception.message
  }
}

async function submit() {
  const priceCents = eurosToCents(form.price)
  if (form.price.trim() && priceCents === null) {
    error.value = t('admin.productForm.priceHint')
    return
  }
  busy.value = true
  error.value = ''
  try {
    created.value = await adminShopApi.createSingle({
      cardId: suggestion.value.card.id,
      cardVariant: form.cardVariant || null,
      condition: form.condition,
      language: form.language,
      priceCents,
      quantity: Number.parseInt(form.quantity, 10) || 0,
      status: form.status,
    })
    suggestion.value = null
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function another() {
  created.value = null
  suggestion.value = null
}
</script>

<template>
  <router-link class="back" to="/admin/producten">
    <ArrowLeft :size="16" aria-hidden="true" />
    {{ t('admin.productForm.back') }}
  </router-link>
  <h1>{{ t('admin.single.title') }}</h1>
  <p class="admin-intro">{{ t('admin.single.intro') }}</p>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <section v-if="created" class="admin-panel">
    <FormMessage type="success">{{ t('admin.single.added', { name: created.name }) }}</FormMessage>
    <div class="admin-actions">
      <router-link class="btn btn-primary btn-small" :to="`/admin/producten/${created.id}`">
        {{ t('admin.single.toProduct') }}
      </router-link>
      <button class="btn btn-secondary btn-small" type="button" @click="another">{{ t('admin.single.another') }}</button>
    </div>
  </section>

  <section v-else-if="!suggestion" class="admin-panel">
    <div class="admin-fields">
      <div class="admin-field">
        <label class="admin-label" :for="field('game')">{{ t('admin.single.game') }}</label>
        <select :id="field('game')" v-model="game" class="admin-input">
          <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
        </select>
      </div>
      <div class="admin-field wide">
        <CardPicker :game="game" :label="t('admin.single.search')" @choose="choose" />
      </div>
    </div>
  </section>

  <template v-else>
    <section class="admin-panel card-panel">
      <CardImage class="card-image" :src="suggestion.card.imageUrl" :alt="suggestion.card.name" />
      <div>
        <h2>{{ suggestion.card.name }}</h2>
        <p class="admin-hint">{{ suggestion.card.setName }}, #{{ suggestion.card.number }}</p>
        <button class="link-button" type="button" @click="suggestion = null">{{ t('admin.productForm.cardChange') }}</button>

        <h3 class="sub-title">{{ t('admin.single.market') }}</h3>
        <p class="admin-hint">{{ t('admin.single.marketHint') }}</p>
        <ul v-if="suggestion.prices.some((price) => price.trendCents !== null)" class="market">
          <li v-for="price in suggestion.prices" :key="price.variant" :class="{ current: price.variant === form.cardVariant }">
            <span>{{ variantLabel(price.variant) }}</span>
            <strong v-if="price.trendEuroCents !== null">{{ formatMoney(price.trendEuroCents, 'EUR') }}</strong>
            <span v-else class="admin-hint">{{ t('catalog.noPrice') }}</span>
            <span v-if="price.currency !== 'EUR' && price.trendCents !== null" class="admin-hint">
              {{ t('admin.single.converted', { price: formatMoney(price.trendCents, price.currency) }) }}
            </span>
            <span class="admin-hint">
              {{ t('catalog.card.source', { source: t(`catalog.priceSources.${price.source}`), date: formatDate(price.updatedAt) }) }}
            </span>
          </li>
        </ul>
        <p v-else class="admin-hint">{{ t('admin.single.marketNone') }}</p>

        <template v-if="suggestion.existing.length">
          <h3 class="sub-title">{{ t('admin.single.existing') }}</h3>
          <ul class="existing">
            <li v-for="row in suggestion.existing" :key="row.id">
              <router-link :to="`/admin/producten/${row.id}`">{{ row.name }}</router-link>
              <span class="admin-hint">
                {{ statusLabel(row.status) }},
                {{ row.priceCents !== null ? formatMoney(row.priceCents, 'EUR') : t('admin.products.noPriceYet') }},
                {{ row.available }} {{ t('admin.stock.available').toLowerCase() }}
              </span>
            </li>
          </ul>
        </template>
      </div>
    </section>

    <form class="admin-panel" @submit.prevent="submit">
      <div class="admin-fields">
        <div v-if="suggestion.card.variants.length" class="admin-field">
          <label class="admin-label" :for="field('variant')">{{ t('admin.single.variant') }}</label>
          <select :id="field('variant')" v-model="form.cardVariant" class="admin-input">
            <option v-for="variant in suggestion.card.variants" :key="variant" :value="variant">
              {{ variantLabel(variant) }}
            </option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('condition')">{{ t('admin.single.condition') }}</label>
          <select :id="field('condition')" v-model="form.condition" class="admin-input">
            <option v-for="condition in CONDITIONS" :key="condition" :value="condition">{{ conditionLabel(condition) }}</option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('language')">{{ t('admin.single.language') }}</label>
          <select :id="field('language')" v-model="form.language" class="admin-input">
            <option v-for="code in LANGUAGES" :key="code" :value="code">{{ languageLabel(code) }}</option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('price')">{{ t('admin.single.price') }}</label>
          <input
            :id="field('price')"
            v-model="form.price"
            class="admin-input"
            inputmode="decimal"
            autocomplete="off"
            :placeholder="marketOfVariant ? formatMoney(marketOfVariant.trendEuroCents, 'EUR') : ''"
          />
          <p class="admin-hint">{{ t('admin.productForm.priceHint') }}</p>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('quantity')">{{ t('admin.single.quantity') }}</label>
          <input :id="field('quantity')" v-model="form.quantity" class="admin-input" inputmode="numeric" />
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('status')">{{ t('admin.single.status') }}</label>
          <select :id="field('status')" v-model="form.status" class="admin-input">
            <option v-for="status in ['ACTIVE', 'DRAFT']" :key="status" :value="status">{{ statusLabel(status) }}</option>
          </select>
        </div>
      </div>
      <div class="admin-actions">
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ t('admin.single.submit') }}</button>
      </div>
    </form>
  </template>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.message {
  margin-top: var(--space-3);
}

.admin-panel {
  max-width: 60rem;
}

.card-panel {
  display: grid;
  grid-template-columns: minmax(0, 8rem) minmax(0, 1fr);
  gap: var(--space-4);
}

.sub-title {
  margin-top: var(--space-4);
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-stretch: 100%;
  font-weight: 700;
}

.market,
.existing {
  display: grid;
  gap: var(--space-2);
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
}

.market li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

.market li.current {
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.existing li {
  display: grid;
  font-size: var(--text-sm);
}
</style>
