<script setup>
import { computed, ref } from 'vue'
import { GAMES } from '../../data/games'
import CardImage from '../../components/catalog/CardImage.vue'
import ListEnd from '../../components/catalog/ListEnd.vue'
import FormMessage from '../../components/FormMessage.vue'
import { adminPriceLinkApi } from '../../services/api'
import { pageFromQuery } from '../../utils/catalog'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'
import { useAdminList } from '../../utils/infiniteList'

/**
 * Pokémon and Magic cards whose Cardmarket product the sync couldn't trust (phase 11). They show their TCGplayer price
 * until the admin picks the right product here, or none. The sync never changes a choice made here.
 */
const props = defineProps({
  game: { type: String, default: undefined },
  status: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const STATUSES = ['doubtful', 'manual']
const LINKED_GAMES = GAMES.filter((item) => ['pokemon', 'magic'].includes(item.slug))

const counts = ref(null)
const actionError = ref('')
const notice = ref('')
const busyId = ref(null)
const typed = ref({})

const current = () => (STATUSES.includes(props.status) ? props.status : 'doubtful')
const currentGame = () => (LINKED_GAMES.some((item) => item.slug === props.game) ? props.game : 'pokemon')

/** More cards load while scrolling (phase 11). */
const links = useAdminList({
  fetchPage: async (page) => {
    const result = await adminPriceLinkApi.list({ game: currentGame(), status: current(), page })
    if (page === 0) counts.value = { doubtful: result.doubtful, manual: result.manual }
    return result
  },
  key: () => [currentGame(), current()],
  page: () => pageFromQuery(props.page),
})
const error = computed(() => actionError.value || links.error?.message || '')

async function act(item, action) {
  busyId.value = item.cardId
  actionError.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = t('admin.priceLinks.saved', { name: `${item.name} (${item.setName} ${item.number})` })
    await links.refresh()
  } catch (exception) {
    actionError.value = exception.message
  } finally {
    busyId.value = null
  }
}

function link(item, productId) {
  return act(item, () => adminPriceLinkApi.set(item.cardId, productId))
}

function linkTyped(item) {
  const id = Number(String(typed.value[item.cardId] ?? '').trim())
  if (!Number.isInteger(id) || id <= 0) return
  return link(item, id)
}

const price = (cents, currency = 'EUR') => (cents == null ? t('admin.priceLinks.noPrice') : formatMoney(cents, currency))
</script>

<template>
  <h1>{{ t('admin.priceLinks.title') }}</h1>
  <p class="admin-intro">{{ t('admin.priceLinks.intro') }}</p>

  <nav class="tabs" :aria-label="t('admin.priceLinks.game')">
    <router-link
      v-for="item in LINKED_GAMES"
      :key="item.slug"
      class="tab"
      :class="{ active: currentGame() === item.slug }"
      :to="{ query: { game: item.slug } }"
    >
      {{ t(item.labelKey) }}
    </router-link>
  </nav>

  <nav class="tabs" :aria-label="t('admin.priceLinks.title')">
    <router-link
      v-for="status in STATUSES"
      :key="status"
      class="tab"
      :class="{ active: current() === status }"
      :to="{ query: { game: currentGame(), status } }"
    >
      {{ t(`admin.priceLinks.${status}`, { count: counts ? counts[status] : '…' }) }}
    </router-link>
  </nav>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <FormMessage v-if="notice" class="message" type="success">{{ notice }}</FormMessage>
  <p v-if="links.loaded && !links.loading && !links.items.length" class="admin-intro message">
    {{ current() === 'manual' ? t('admin.priceLinks.emptyManual') : t('admin.priceLinks.empty') }}
  </p>

  <ul v-if="links.items.length" class="list">
    <li v-for="item in links.items" :key="item.cardId" class="item admin-panel">
      <router-link class="picture" :to="`/kaarten/${item.game}/${encodeURIComponent(item.setCode)}/${item.cardId}`" target="_blank">
        <CardImage :src="item.imageUrl" :alt="item.name" />
      </router-link>

      <div class="details">
        <p class="name">{{ item.name }}</p>
        <p class="admin-hint">
          {{ [item.setName, t('admin.priceLinks.number', { number: item.number }), item.rarity].filter(Boolean).join(' · ') }}
        </p>

        <p class="admin-label">{{ t('admin.priceLinks.now') }}</p>
        <ul v-if="item.prices.length" class="prices">
          <li v-for="entry in item.prices" :key="`${entry.variant}-${entry.source}`">
            {{ entry.variant }}: <strong>{{ price(entry.trendCents, entry.currency) }}</strong>
            <span class="admin-hint">
              {{ t(`admin.priceLinks.source.${entry.source}`) }}, {{ formatDate(entry.updatedAt) }}
            </span>
          </li>
        </ul>
        <p v-else class="admin-hint">{{ t('admin.priceLinks.noPrice') }}</p>

        <p v-if="item.product" class="admin-hint">
          {{ item.link === 'MANUAL' ? t('admin.priceLinks.chosen') : t('admin.priceLinks.fromSource') }}:
          {{ item.product.productId }} · {{ item.product.name }} · {{ price(item.product.trendCents) }}
        </p>

        <p class="admin-label">{{ t('admin.priceLinks.candidates') }}</p>
        <ul v-if="item.candidates.length" class="candidates">
          <li v-for="candidate in item.candidates" :key="candidate.productId" class="candidate">
            <span class="candidate-text">
              <strong>{{ price(candidate.trendCents) }}</strong>
              <span v-if="candidate.foilTrendCents" class="admin-hint">
                {{ t('admin.priceLinks.foil', { price: price(candidate.foilTrendCents) }) }}
              </span>
              <span class="admin-hint">{{ candidate.productId }} · {{ candidate.name }}</span>
            </span>
            <button
              class="btn btn-secondary btn-small"
              type="button"
              :disabled="busyId === item.cardId || (item.link === 'MANUAL' && item.product?.productId === candidate.productId)"
              @click="link(item, candidate.productId)"
            >
              {{ t('admin.priceLinks.use') }}
            </button>
          </li>
        </ul>
        <p v-else class="admin-hint">{{ t('admin.priceLinks.noCandidates') }}</p>

        <form class="other" @submit.prevent="linkTyped(item)">
          <label class="admin-label" :for="`product-${item.cardId}`">{{ t('admin.priceLinks.otherId') }}</label>
          <div class="other-row">
            <input
              :id="`product-${item.cardId}`"
              v-model="typed[item.cardId]"
              class="admin-input"
              inputmode="numeric"
              :placeholder="t('admin.priceLinks.otherIdPlaceholder')"
            />
            <button class="btn btn-secondary btn-small" type="submit" :disabled="busyId === item.cardId">
              {{ t('admin.priceLinks.useId') }}
            </button>
          </div>
        </form>

        <div class="admin-actions">
          <button class="link-button" type="button" :disabled="busyId === item.cardId" @click="link(item, null)">
            {{ t('admin.priceLinks.none') }}
          </button>
          <button
            v-if="item.link === 'MANUAL'"
            class="link-button"
            type="button"
            :title="t('admin.priceLinks.resetHint')"
            :disabled="busyId === item.cardId"
            @click="act(item, () => adminPriceLinkApi.reset(item.cardId))"
          >
            {{ t('admin.priceLinks.reset') }}
          </button>
        </div>
      </div>
    </li>
  </ul>
  <ListEnd v-if="links.items.length" :list="links" />
</template>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.tabs + .tabs {
  margin-top: var(--space-2);
}

.tab {
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.tab.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.message {
  margin-top: var(--space-4);
}

.list {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.item {
  display: grid;
  gap: var(--space-3);
}

.picture {
  display: block;
  width: min(10rem, 100%);
}

.details {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
}

.name {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.prices,
.candidates {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.candidate {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
}

.candidate-text {
  display: grid;
  min-width: 0;
  overflow-wrap: anywhere;
}

.other-row {
  display: flex;
  gap: var(--space-2);
}

.other-row .admin-input {
  max-width: 12rem;
}

@media (min-width: 720px) {
  .item {
    grid-template-columns: 10rem minmax(0, 1fr);
    align-items: start;
  }
}
</style>
