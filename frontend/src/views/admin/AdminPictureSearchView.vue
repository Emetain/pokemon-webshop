<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ExternalLink, Search } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import ListEnd from '../../components/catalog/ListEnd.vue'
import { GAMES, findGame } from '../../data/games'
import { adminPictureApi } from '../../services/api'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'
import { useAdminList } from '../../utils/infiniteList'

/**
 * Pictures from TCGplayer for cards and products without one. A search per game fills in the sure matches by itself
 * and puts the doubtful ones below, where the admin picks the right picture or none.
 */
const POLL_MS = 3000

const overview = ref([])
const gameFilter = ref('')
const busyId = ref(null)
const actionError = ref('')
let poll = null

const running = computed(() => overview.value.some((row) => row.search?.running))
const gameLabel = (slug) => t(findGame(slug)?.labelKey ?? '')

async function loadOverview() {
  try {
    overview.value = await adminPictureApi.overview()
  } catch (exception) {
    actionError.value = exception.message
  }
  clearTimeout(poll)
  if (running.value) poll = setTimeout(refreshWhileRunning, POLL_MS)
}

/** While a search runs, the counts and the review list grow: ask again. */
async function refreshWhileRunning() {
  await loadOverview()
  if (!running.value) loadSuggestions()
}

/** The doubtful pictures, more while scrolling (phase 11). The game filter isn't in the URL, so neither is the depth. */
const suggestions = useAdminList({
  fetchPage: async (page) => {
    const result = await adminPictureApi.suggestions({ game: gameFilter.value || undefined, page })
    return { items: result.suggestions, totalPages: result.totalPages, totalItems: result.total }
  },
  key: () => [gameFilter.value],
  page: () => 1,
  syncUrl: false,
})
const error = computed(() => actionError.value || suggestions.error?.message || '')

function loadSuggestions() {
  return suggestions.refresh()
}

async function start(game) {
  actionError.value = ''
  try {
    await adminPictureApi.start(game)
    await loadOverview()
  } catch (exception) {
    actionError.value = exception.message
  }
}

async function decide(suggestion, action) {
  busyId.value = suggestion.id
  actionError.value = ''
  try {
    await action()
    // Gone from the list at once; the reload keeps the pages after it in step with the server.
    suggestions.items = suggestions.items.filter((item) => item.id !== suggestion.id)
    loadSuggestions()
    loadOverview()
  } catch (exception) {
    actionError.value = exception.message
  } finally {
    busyId.value = null
  }
}

function detail(suggestion) {
  if (suggestion.kind === 'CARD') return t('admin.pictures.number', { number: suggestion.detail })
  return suggestion.detail ? t(`shop.types.${suggestion.detail}`) : ''
}

onMounted(loadOverview)

onBeforeUnmount(() => clearTimeout(poll))
</script>

<template>
  <router-link class="back" to="/admin/afbeeldingen">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ t('admin.images.title') }}
  </router-link>
  <h1>{{ t('admin.pictures.title') }}</h1>
  <p class="admin-intro">{{ t('admin.pictures.intro') }}</p>
  <p class="admin-intro">{{ t('admin.pictures.rights') }}</p>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <ul class="games">
    <li v-for="row in overview" :key="row.game" class="game admin-panel" :data-game="findGame(row.game)?.accent">
      <h2>{{ gameLabel(row.game) }}</h2>
      <p class="admin-hint">
        {{ t('admin.pictures.missing', { cards: formatNumber(row.cardsWithoutPicture), products: formatNumber(row.productsWithoutPicture) }) }}
      </p>
      <p v-if="row.pendingSuggestions" class="pending">{{ t('admin.pictures.pending', { count: formatNumber(row.pendingSuggestions) }) }}</p>

      <div v-if="row.search" class="status" role="status">
        <template v-if="row.search.running">
          <p class="running">{{ t('admin.pictures.running', { done: row.search.setsDone, total: row.search.setsTotal }) }}</p>
          <progress :value="row.search.setsDone" :max="Math.max(1, row.search.setsTotal)"></progress>
        </template>
        <FormMessage v-else-if="row.search.error">{{ t('admin.pictures.failed', { error: row.search.error }) }}</FormMessage>
        <p v-else class="admin-hint">{{ t('admin.pictures.finished', { date: formatDateTime(row.search.finishedAt) }) }}</p>
        <ul class="counts">
          <li>{{ t('admin.pictures.cardsFilled', { count: row.search.cardsFilled }) }}</li>
          <li>{{ t('admin.pictures.productsFilled', { count: row.search.productsFilled }) }}</li>
          <li>{{ t('admin.pictures.suggested', { count: row.search.suggested }) }}</li>
          <li>{{ t('admin.pictures.notFound', { count: row.search.notFound, sets: row.search.setsNotFound }) }}</li>
          <li v-if="row.search.skipped">{{ t('admin.pictures.skipped', { count: row.search.skipped }) }}</li>
        </ul>
      </div>

      <button
        class="btn btn-primary btn-small"
        type="button"
        :disabled="row.search?.running || (!row.cardsWithoutPicture && !row.productsWithoutPicture)"
        @click="start(row.game)"
      >
        <Search :size="16" aria-hidden="true" />
        {{ row.search?.running ? t('admin.pictures.searching') : t('admin.pictures.search') }}
      </button>
    </li>
  </ul>

  <section class="review" aria-labelledby="pictures-review">
    <div class="review-head">
      <div>
        <h2 id="pictures-review">{{ t('admin.pictures.reviewTitle') }}</h2>
        <p class="admin-hint">{{ t('admin.pictures.reviewIntro') }}</p>
      </div>
      <label class="filter">
        <span class="admin-label">{{ t('admin.images.game') }}</span>
        <select v-model="gameFilter" class="admin-input">
          <option value="">{{ t('admin.images.allGames') }}</option>
          <option v-for="game in GAMES" :key="game.slug" :value="game.slug">{{ t(game.labelKey) }}</option>
        </select>
      </label>
    </div>

    <p v-if="suggestions.loaded && !suggestions.loading && !suggestions.items.length" class="admin-intro">
      {{ t('admin.pictures.reviewEmpty') }}
    </p>
    <ul v-else-if="suggestions.items.length" class="suggestions">
      <li v-for="suggestion in suggestions.items" :key="suggestion.id" class="suggestion admin-panel">
        <div class="ours">
          <p class="name">
            <router-link v-if="suggestion.productId" :to="`/admin/producten/${suggestion.productId}`">{{ suggestion.name }}</router-link>
            <template v-else>{{ suggestion.name }}</template>
          </p>
          <p class="admin-hint">{{ [gameLabel(suggestion.game), suggestion.setName, detail(suggestion)].filter(Boolean).join(' · ') }}</p>
          <button class="link-button" type="button" :disabled="busyId === suggestion.id" @click="decide(suggestion, () => adminPictureApi.reject(suggestion.id))">
            {{ t('admin.pictures.none') }}
          </button>
        </div>
        <ul class="candidates">
          <li v-for="candidate in suggestion.candidates" :key="candidate.tcgplayerId" class="candidate">
            <a class="picture" :href="candidate.largeImageUrl" target="_blank" rel="noopener" :title="t('admin.pictures.enlarge')">
              <img :src="candidate.imageUrl" :alt="candidate.name" loading="lazy" decoding="async" />
              <ExternalLink class="enlarge" :size="14" aria-hidden="true" />
            </a>
            <p class="candidate-name">{{ candidate.name }}</p>
            <button
              class="btn btn-secondary btn-small"
              type="button"
              :disabled="busyId === suggestion.id"
              @click="decide(suggestion, () => adminPictureApi.accept(suggestion.id, candidate.tcgplayerId))"
            >
              {{ t('admin.pictures.use') }}
            </button>
          </li>
        </ul>
      </li>
    </ul>
    <p v-if="suggestions.totalItems" class="admin-hint count">
      {{ t('admin.pictures.total', { count: formatNumber(suggestions.totalItems) }) }}
    </p>
    <ListEnd v-if="suggestions.items.length" :list="suggestions" />
  </section>
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
  margin-top: var(--space-4);
}

.games {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 15rem), 1fr));
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.game {
  display: grid;
  align-content: start;
  justify-items: start;
  gap: var(--space-2);
  border-top: 4px solid var(--accent, var(--color-brand));
}

.game h2 {
  font-size: var(--text-lg);
}

.pending {
  color: var(--color-warning);
  font-size: var(--text-sm);
  font-weight: 700;
}

.status {
  display: grid;
  gap: var(--space-1);
  width: 100%;
}

.running {
  font-size: var(--text-sm);
  font-weight: 650;
}

progress {
  width: 100%;
  accent-color: var(--color-brand);
}

.counts {
  margin: 0;
  padding-left: var(--space-4);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.review {
  margin-top: var(--space-6);
}

.review-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: flex-end;
  justify-content: space-between;
}

.review-head h2 {
  font-size: var(--text-xl);
}

.filter {
  display: grid;
  gap: var(--space-1);
}

.suggestions {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.suggestion {
  display: grid;
  gap: var(--space-3);
}

.ours {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
}

.name {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 11rem), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.candidate {
  display: grid;
  align-content: start;
  justify-items: start;
  gap: var(--space-2);
}

/* Every picture gets the same height and is shown whole: a tall blister and a wide bundle alike. */
.picture {
  position: relative;
  display: block;
  width: 100%;
  height: 12rem;
  padding: var(--space-2);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.picture img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.enlarge {
  position: absolute;
  top: var(--space-1);
  right: var(--space-1);
  color: var(--color-ink-soft);
}

.candidate-name {
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.count {
  margin-top: var(--space-3);
}

@media (min-width: 900px) {
  .suggestion {
    grid-template-columns: 16rem minmax(0, 1fr);
    align-items: start;
  }
}
</style>
