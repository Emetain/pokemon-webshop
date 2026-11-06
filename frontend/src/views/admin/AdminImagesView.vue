<script setup>
import { computed, ref, watch } from 'vue'
import { RotateCcw, Upload } from 'lucide-vue-next'
import CardImage from '../../components/catalog/CardImage.vue'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { adminApi, catalogApi } from '../../services/api'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const MIN_SEARCH_LENGTH = 2
const SEARCH_DELAY_MS = 300
const CARD_RESULTS = 24
const SET_RESULTS = 30

const kind = ref('cards')
const game = ref('')
const setGame = ref(GAMES[0].slug)
const query = ref('')

const cards = ref([])
const sets = ref([])
const loading = ref(false)
const busyItem = ref(null)
const error = ref('')
const message = ref('')

const gameLabel = (slug) => t(GAMES.find((item) => item.slug === slug).labelKey)

const matchingSets = computed(() => {
  const term = query.value.trim().toLowerCase()
  if (!term) return sets.value
  return sets.value.filter((set) => set.name.toLowerCase().includes(term) || set.code.toLowerCase().includes(term))
})

const shownSets = computed(() => matchingSets.value.slice(0, SET_RESULTS))
const hiddenSets = computed(() => matchingSets.value.length - shownSets.value.length)

const canSearchCards = computed(() => query.value.trim().length >= MIN_SEARCH_LENGTH)

let latestRequest = 0
let searchTimer = null

async function searchCards() {
  const request = ++latestRequest
  if (!canSearchCards.value) {
    cards.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await catalogApi.search({ q: query.value.trim(), game: game.value, size: CARD_RESULTS })
    if (request === latestRequest) cards.value = result.results.items
  } catch (exception) {
    if (request === latestRequest) error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

async function loadSets() {
  const request = ++latestRequest
  loading.value = true
  try {
    const result = await catalogApi.sets(setGame.value)
    if (request === latestRequest) sets.value = result
  } catch (exception) {
    if (request === latestRequest) error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

function clearMessages() {
  error.value = ''
  message.value = ''
}

async function change(key, action, done) {
  busyItem.value = key
  clearMessages()
  try {
    await action()
    message.value = done
  } catch (exception) {
    error.value = exception.message
  } finally {
    busyItem.value = null
  }
}

function chosenFile(event) {
  const file = event.target.files[0]
  // Choosing the same file again has to work too, for example after an error.
  event.target.value = ''
  if (!file) return null
  if (file.size > MAX_FILE_BYTES) {
    clearMessages()
    error.value = t('admin.images.tooLarge')
    return null
  }
  return file
}

function uploadCard(card, event) {
  const file = chosenFile(event)
  if (!file) return
  change(
    `card-${card.id}`,
    async () => {
      const image = await adminApi.uploadCardImage(card.id, file)
      card.imageUrl = image.imageUrl
      card.customImage = image.custom
    },
    t('admin.images.saved'),
  )
}

function resetCard(card) {
  change(
    `card-${card.id}`,
    async () => {
      const image = await adminApi.resetCardImage(card.id)
      card.imageUrl = image.imageUrl
      card.customImage = image.custom
    },
    t('admin.images.resetDone'),
  )
}

function uploadSet(set, event) {
  const file = chosenFile(event)
  if (!file) return
  change(
    `set-${set.code}`,
    async () => {
      const image = await adminApi.uploadSetLogo(set.game, set.code, file)
      set.logoUrl = image.imageUrl
      set.customLogo = image.custom
    },
    t('admin.images.saved'),
  )
}

function resetSet(set) {
  change(
    `set-${set.code}`,
    async () => {
      const image = await adminApi.resetSetLogo(set.game, set.code)
      set.logoUrl = image.imageUrl
      set.customLogo = image.custom
    },
    t('admin.images.resetDone'),
  )
}

watch(kind, (value) => {
  query.value = ''
  clearMessages()
  if (value === 'sets') loadSets()
})

watch(setGame, loadSets)

watch([query, game], () => {
  if (kind.value !== 'cards') return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(searchCards, SEARCH_DELAY_MS)
})
</script>

<template>
  <h1>{{ t('admin.images.title') }}</h1>
  <p class="intro">{{ t('admin.images.intro') }}</p>
  <p class="intro">{{ t('admin.images.rights') }}</p>

  <div class="kinds" role="group" :aria-label="t('admin.images.kind')">
    <button class="kind" type="button" :aria-pressed="kind === 'cards'" @click="kind = 'cards'">
      {{ t('admin.images.cards') }}
    </button>
    <button class="kind" type="button" :aria-pressed="kind === 'sets'" @click="kind = 'sets'">
      {{ t('admin.images.sets') }}
    </button>
  </div>

  <div class="search">
    <label class="field grow">
      <span class="label">{{ kind === 'cards' ? t('admin.images.searchCards') : t('admin.images.searchSets') }}</span>
      <input v-model="query" class="input" type="search" autocomplete="off" />
    </label>
    <label class="field">
      <span class="label">{{ t('admin.images.game') }}</span>
      <select v-if="kind === 'cards'" v-model="game" class="input">
        <option value="">{{ t('admin.images.allGames') }}</option>
        <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
      </select>
      <select v-else v-model="setGame" class="input">
        <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
      </select>
    </label>
  </div>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <FormMessage v-if="message" class="message" type="success">{{ message }}</FormMessage>

  <template v-if="kind === 'cards'">
    <p v-if="!canSearchCards" class="hint">{{ t('admin.images.searchHint') }}</p>
    <p v-else-if="loading" class="hint" role="status">{{ t('admin.images.loading') }}</p>
    <p v-else-if="!cards.length" class="hint">{{ t('admin.images.noResults') }}</p>

    <ul v-else class="items">
      <li v-for="card in cards" :key="card.id" class="item">
        <CardImage class="thumb" :src="card.imageUrl" :alt="card.name" />
        <div class="text">
          <p class="name">{{ card.name }}</p>
          <p class="details">{{ gameLabel(card.game) }}, {{ card.setName }}, #{{ card.number }}</p>
          <p v-if="card.customImage" class="custom">{{ t('admin.images.custom') }}</p>
        </div>
        <div class="actions">
          <label class="btn btn-secondary action" :class="{ busy: busyItem === `card-${card.id}` }">
            <Upload :size="16" aria-hidden="true" />
            {{ t('admin.images.choose') }}
            <input
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :aria-label="t('admin.images.chooseLabel', { name: card.name })"
              :disabled="busyItem === `card-${card.id}`"
              @change="uploadCard(card, $event)"
            />
          </label>
          <button
            v-if="card.customImage"
            class="text-button"
            type="button"
            :aria-label="t('admin.images.resetLabel', { name: card.name })"
            :disabled="busyItem === `card-${card.id}`"
            @click="resetCard(card)"
          >
            <RotateCcw :size="16" aria-hidden="true" />
            {{ t('admin.images.reset') }}
          </button>
        </div>
      </li>
    </ul>
  </template>

  <template v-else>
    <p v-if="loading" class="hint" role="status">{{ t('admin.images.loading') }}</p>
    <p v-else-if="!shownSets.length" class="hint">{{ t('admin.images.noResults') }}</p>

    <ul v-else class="items">
      <li v-for="set in shownSets" :key="set.code" class="item">
        <span class="logo-box">
          <img v-if="set.logoUrl" class="logo" :src="set.logoUrl" alt="" loading="lazy" decoding="async" />
          <span v-else class="code">{{ set.code }}</span>
        </span>
        <div class="text">
          <p class="name">{{ set.name }}</p>
          <p class="details">{{ set.code }}</p>
          <p v-if="set.customLogo" class="custom">{{ t('admin.images.custom') }}</p>
        </div>
        <div class="actions">
          <label class="btn btn-secondary action" :class="{ busy: busyItem === `set-${set.code}` }">
            <Upload :size="16" aria-hidden="true" />
            {{ t('admin.images.choose') }}
            <input
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :aria-label="t('admin.images.chooseLabel', { name: set.name })"
              :disabled="busyItem === `set-${set.code}`"
              @change="uploadSet(set, $event)"
            />
          </label>
          <button
            v-if="set.customLogo"
            class="text-button"
            type="button"
            :aria-label="t('admin.images.resetLabel', { name: set.name })"
            :disabled="busyItem === `set-${set.code}`"
            @click="resetSet(set)"
          >
            <RotateCcw :size="16" aria-hidden="true" />
            {{ t('admin.images.reset') }}
          </button>
        </div>
      </li>
    </ul>
    <p v-if="hiddenSets > 0" class="hint">{{ t('admin.images.moreSets', { count: hiddenSets }) }}</p>
  </template>
</template>

<style scoped>
.intro {
  max-width: 60ch;
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
}

.kinds {
  display: inline-flex;
  margin-top: var(--space-4);
  padding: var(--space-half);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
}

.kind {
  min-height: var(--touch-target);
  padding-inline: var(--space-4);
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.kind[aria-pressed='true'] {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.search {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.field {
  display: block;
  flex: 1 1 10rem;
  min-width: 0;
  max-width: 14rem;
}

.field.grow {
  flex: 3 1 16rem;
  max-width: 30rem;
}

.label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.input {
  width: 100%;
  height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: var(--text-sm);
}

.message {
  margin-top: var(--space-3);
}

.hint {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.items {
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.item {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2) var(--space-3);
  padding-block: var(--space-3);
  border-top: 1px solid var(--color-line);
}

.thumb {
  width: 4.5rem;
}

.logo-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 3rem;
}

.logo {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.code {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.text {
  min-width: 0;
}

.name {
  font-size: var(--text-sm);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.details {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.custom {
  margin-top: var(--space-half);
  color: var(--color-brand);
  font-size: var(--text-xs);
  font-weight: 600;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  grid-column: 1 / -1;
  align-items: center;
  gap: var(--space-1) var(--space-3);
}

.action {
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  font-size: var(--text-sm);
}

/* The input is hidden, so its label shows the keyboard focus. */
.action:focus-within {
  outline: 3px solid var(--color-focus);
  outline-offset: 2px;
}

.action.busy {
  cursor: progress;
  opacity: 0.6;
}

.text-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.text-button:hover:not(:disabled) {
  text-decoration: underline;
}

@media (min-width: 640px) {
  .item {
    grid-template-columns: 4.5rem minmax(0, 1fr) auto;
  }

  .actions {
    grid-column: auto;
    justify-content: flex-end;
  }
}
</style>
