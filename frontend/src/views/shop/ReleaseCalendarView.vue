<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CalendarClock, CalendarDays } from 'lucide-vue-next'
import DateStamp from '../../components/shop/DateStamp.vue'
import FormMessage from '../../components/FormMessage.vue'
import GameFilter from '../../components/shop/GameFilter.vue'
import ProductTile from '../../components/shop/ProductTile.vue'
import { findGame } from '../../data/games'
import { releaseApi } from '../../services/api'
import { daysUntil, formatDate, formatMonth } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Upcoming sets per game, managed in admin, each with its pre-orders. A hero slide links to `#release-{id}`. */
const props = defineProps({
  game: { type: String, default: undefined },
})

const route = useRoute()
const calendar = ref(null)
const error = ref('')

async function load() {
  calendar.value = null
  error.value = ''
  try {
    calendar.value = await releaseApi.calendar(props.game)
    // The page arrived from a slide's link: the release only exists once the list is there.
    if (route.hash) {
      await nextTick()
      document.querySelector(route.hash)?.scrollIntoView({ block: 'start' })
    }
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => props.game, load, { immediate: true })

const months = computed(() => {
  const byMonth = new Map()
  for (const release of calendar.value?.upcoming ?? []) {
    const key = release.releaseDate.slice(0, 7)
    if (!byMonth.has(key)) byMonth.set(key, [])
    byMonth.get(key).push(release)
  }
  return [...byMonth.entries()].map(([key, releases]) => ({ key, title: formatMonth(`${key}-01`), releases }))
})

const accent = (release) => findGame(release.game)?.accent
const gameLabel = (release) => t(findGame(release.game)?.labelKey ?? '')

function when(release) {
  if (release.tentative) return t('releases.expectedIn', { month: formatMonth(release.releaseDate) })
  const days = daysUntil(release.releaseDate)
  const date = formatDate(release.releaseDate)
  if (days === 0) return t('releases.today', { date })
  if (days > 0) return t('releases.inDays', { date, days })
  return t('releases.releasedOn', { date })
}
</script>

<template>
  <div class="container page">
    <header class="head">
      <div>
        <h1><CalendarDays class="title-icon" :size="28" aria-hidden="true" />{{ t('releases.title') }}</h1>
        <p class="intro">{{ t('releases.intro') }}</p>
      </div>
      <router-link class="btn btn-secondary btn-small" to="/pre-order">
        <CalendarClock :size="18" aria-hidden="true" />
        {{ t('releases.toPreOrders') }}
      </router-link>
    </header>

    <GameFilter :current="game ?? null" :label="t('releases.filterLabel')" />

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
    <p v-else-if="!calendar" class="muted message" aria-busy="true">{{ t('releases.loading') }}</p>

    <template v-else>
      <p v-if="!months.length" class="muted message">{{ game ? t('releases.emptyGame') : t('releases.empty') }}</p>

      <section v-for="month in months" :key="month.key" class="month" :aria-labelledby="`releases-${month.key}`">
        <h2 :id="`releases-${month.key}`" class="month-title">{{ month.title }}</h2>
        <ol class="releases">
          <li
            v-for="release in month.releases"
            :id="`release-${release.id}`"
            :key="release.id"
            class="release"
            :data-game="accent(release)"
          >
            <DateStamp :date="release.releaseDate" :tentative="release.tentative" />
            <div class="body">
              <p class="game">{{ gameLabel(release) }}</p>
              <h3 class="name">{{ release.name }}</h3>
              <p class="when">{{ when(release) }}</p>
              <p v-if="release.description" class="description">{{ release.description }}</p>
              <ul v-if="release.preOrders.length" class="pre-orders" :aria-label="t('releases.preOrdersOf', { name: release.name })">
                <li v-for="product in release.preOrders" :key="product.id">
                  <ProductTile :product="product" @wished="product.wished = $event" @error="error = $event" />
                </li>
              </ul>
              <p v-else class="muted small">{{ t('releases.noPreOrders') }}</p>
            </div>
            <img v-if="release.imageUrl" class="picture" :src="release.imageUrl" alt="" loading="lazy" decoding="async" />
          </li>
        </ol>
      </section>

      <section v-if="calendar.recent.length" class="month recent" aria-labelledby="releases-recent">
        <h2 id="releases-recent" class="month-title">{{ t('releases.recent') }}</h2>
        <ul class="recent-list">
          <li v-for="release in calendar.recent" :id="`release-${release.id}`" :key="release.id" :data-game="accent(release)">
            <span class="dot" aria-hidden="true"></span>
            <strong>{{ release.name }}</strong>
            <span class="muted">{{ gameLabel(release) }}, {{ when(release) }}</span>
          </li>
        </ul>
      </section>
    </template>
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

.muted {
  color: var(--color-ink-soft);
}

.small {
  font-size: var(--text-sm);
}

.month {
  margin-top: var(--space-6);
}

.month-title {
  font-size: var(--text-xl);
}

.month-title::first-letter {
  text-transform: uppercase;
}

.releases {
  display: grid;
  gap: var(--space-4);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

/* A release: the date sheet in the colour of its game, then the set and its pre-orders. */
.release {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-left: 5px solid var(--accent);
  border-radius: var(--radius-md);
  scroll-margin-top: 8rem;
}

.release:target {
  box-shadow: 0 0 0 3px var(--color-focus);
}

.body {
  min-width: 0;
}

.game {
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 700;
}

.name {
  font-size: var(--text-xl);
}

.when {
  margin-top: var(--space-1);
  font-weight: 600;
}

.description {
  max-width: 65ch;
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}

.pre-orders {
  display: grid;
  grid-auto-columns: minmax(10rem, 12rem);
  grid-auto-flow: column;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0 0 var(--space-2);
  overflow-x: auto;
  list-style: none;
}

.picture {
  grid-column: 1 / -1;
  width: 100%;
  max-height: 14rem;
  border-radius: var(--radius-sm);
  object-fit: cover;
}

@media (min-width: 900px) {
  .release {
    grid-template-columns: auto minmax(0, 1fr) 16rem;
  }

  .picture {
    grid-column: auto;
    height: 100%;
    max-height: 12rem;
  }
}

.recent-list {
  display: grid;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.recent-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-2);
}

.dot {
  width: 0.625rem;
  height: 0.625rem;
  border-radius: 50%;
  background: var(--accent);
}
</style>
