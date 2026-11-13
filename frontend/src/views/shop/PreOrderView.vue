<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays, CalendarClock } from 'lucide-vue-next'
import DateStamp from '../../components/shop/DateStamp.vue'
import FormMessage from '../../components/FormMessage.vue'
import GameFilter from '../../components/shop/GameFilter.vue'
import ProductGrid from '../../components/shop/ProductGrid.vue'
import { releaseApi } from '../../services/api'
import { daysUntil, formatDate, formatMonth } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Every pre-order on a timeline: a month per section, a release date per stop, the products of that day beside it. */
const props = defineProps({
  game: { type: String, default: undefined },
})

const products = ref(null)
const error = ref('')

async function load() {
  products.value = null
  error.value = ''
  try {
    products.value = await releaseApi.preOrders(props.game)
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => props.game, load, { immediate: true })

/** Months, oldest first, each with its release days. Products without a date go in one group at the end. */
const months = computed(() => {
  const byMonth = new Map()
  for (const product of products.value ?? []) {
    const date = product.releaseDate
    const monthKey = date ? date.slice(0, 7) : 'later'
    if (!byMonth.has(monthKey)) byMonth.set(monthKey, new Map())
    const days = byMonth.get(monthKey)
    const dayKey = date ?? 'later'
    if (!days.has(dayKey)) days.set(dayKey, [])
    days.get(dayKey).push(product)
  }
  return [...byMonth.entries()].map(([key, days]) => ({
    key,
    title: key === 'later' ? t('preOrders.noDate') : formatMonth(`${key}-01`),
    days: [...days.entries()].map(([date, items]) => ({ date: date === 'later' ? null : date, products: items })),
  }))
})

function countdown(date) {
  const days = daysUntil(date)
  if (days < 0) return t('preOrders.released')
  if (days === 0) return t('preOrders.today')
  if (days === 1) return t('preOrders.tomorrow')
  return t('preOrders.inDays', { days })
}

function onWished(product, wished) {
  product.wished = wished
}
</script>

<template>
  <div class="container page">
    <header class="head">
      <div>
        <h1><CalendarClock class="title-icon" :size="28" aria-hidden="true" />{{ t('preOrders.title') }}</h1>
        <p class="intro">{{ t('preOrders.intro') }}</p>
      </div>
      <router-link class="btn btn-secondary btn-small" to="/releasekalender">
        <CalendarDays :size="18" aria-hidden="true" />
        {{ t('preOrders.toCalendar') }}
      </router-link>
    </header>

    <GameFilter :current="game ?? null" :label="t('preOrders.filterLabel')" />

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
    <ProductGrid v-else-if="!products" class="message" loading />
    <div v-else-if="!products.length" class="empty">
      <p>{{ game ? t('preOrders.emptyGame') : t('preOrders.empty') }}</p>
      <router-link class="btn btn-primary" to="/releasekalender">{{ t('preOrders.toCalendar') }}</router-link>
    </div>

    <div v-else class="timeline">
      <section v-for="month in months" :key="month.key" class="month" :aria-labelledby="`month-${month.key}`">
        <h2 :id="`month-${month.key}`" class="month-title">{{ month.title }}</h2>
        <ol class="stops">
          <li v-for="day in month.days" :key="day.date ?? 'later'" class="stop">
            <div class="when">
              <DateStamp v-if="day.date" :date="day.date" />
              <div v-if="day.date" class="when-text">
                <h3 class="date">{{ formatDate(day.date) }}</h3>
                <p class="countdown">{{ countdown(day.date) }}</p>
              </div>
            </div>
            <ProductGrid class="products" :products="day.products" narrow @wished="onWished" @error="error = $event" />
          </li>
        </ol>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-5) 0;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
  margin-bottom: var(--space-4);
}

h1 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.title-icon {
  color: var(--color-brand);
}

.intro {
  max-width: 60ch;
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-lg);
}

.message {
  margin-top: var(--space-5);
}

.empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  margin-top: var(--space-6);
  color: var(--color-ink-soft);
}

.timeline {
  margin-top: var(--space-6);
}

.month + .month {
  margin-top: var(--space-7);
}

.month-title {
  font-size: var(--text-xl);
}

.month-title::first-letter {
  text-transform: uppercase;
}

/* The rail: one line down the left, a stop per release date. */
.stops {
  margin: var(--space-3) 0 0;
  padding: 0 0 0 var(--space-5);
  border-left: 3px solid var(--color-tint-strong);
  list-style: none;
}

.stop {
  position: relative;
}

.stop + .stop {
  margin-top: var(--space-6);
}

.stop::before {
  position: absolute;
  top: 1.5rem;
  left: calc(-1 * var(--space-5) - 9px);
  width: 15px;
  height: 15px;
  border: 3px solid var(--color-page);
  border-radius: 50%;
  background: var(--color-brand);
  content: '';
}

.when {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.date {
  font-size: var(--text-lg);
}

.countdown {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 600;
}

@media (min-width: 1024px) {
  .stop {
    display: grid;
    grid-template-columns: 13rem minmax(0, 1fr);
    gap: var(--space-5);
    align-items: start;
  }

  .when {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
