<script setup>
import { computed, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { RotateCcw, Upload } from 'lucide-vue-next'
import CardImage from '../../components/catalog/CardImage.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { adminApi, catalogApi } from '../../services/api'
import { dexLabel, filterQuery, pageFromQuery } from '../../utils/catalog'
import { formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const SEARCH_DELAY_MS = 350
const SETS_PER_PAGE = 30

/** Filters, sorting and page live in the URL, like on the card lists, so a list can be bookmarked. */
const props = defineProps({
  kind: { type: String, default: 'cards' },
  q: { type: String, default: '' },
  game: { type: String, default: '' },
  set: { type: String, default: '' },
  image: { type: String, default: '' },
  sort: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const ids = { search: useId(), game: useId(), set: useId(), image: useId(), sort: useId() }

const cards = ref(null)
const sets = ref([])
const loading = ref(false)
const busyItem = ref(null)
const error = ref('')
const message = ref('')
const searchText = ref(props.q)

const showingSets = computed(() => props.kind === 'sets')
const currentPage = computed(() => pageFromQuery(props.page))
const setGame = computed(() => props.game || (showingSets.value ? GAMES[0].slug : ''))
const hasFilters = computed(() => Boolean(props.q || props.game || props.set || props.image))

function cardDetails(card) {
  const parts = [gameLabel(card.game), card.setName, `#${card.number}`]
  if (card.dexNumbers.length) parts.push(`${t('catalog.card.pokedex')} ${card.dexNumbers.map(dexLabel).join(' ')}`)
  return parts.join(', ')
}

const gameLabel = (slug) => t(GAMES.find((item) => item.slug === slug).labelKey)

const countLabel = (count, one, many) => (count === 1 ? t(one) : t(many, { count: formatNumber(count) }))

/** Changes the URL. Leaving out the page starts at page 1, because the list has changed. */
function update(changes) {
  const values = { kind: props.kind, q: props.q, game: props.game, set: props.set, image: props.image, sort: props.sort }
  router.replace({ query: filterQuery({ ...values, ...changes, kind: (changes.kind ?? props.kind) === 'sets' ? 'sets' : '' }) })
}

function filter(name) {
  return computed({
    get: () => props[name],
    set: (value) => update({ [name]: value }),
  })
}

// The sets of one game are another list, so choosing a game clears the set.
const gameFilter = computed({
  get: () => (showingSets.value ? setGame.value : props.game),
  set: (value) => update({ game: value, set: '' }),
})
const setFilter = filter('set')
const imageFilter = filter('image')
const sortFilter = filter('sort')

const matchingSets = computed(() => {
  const term = props.q.trim().toLowerCase()
  return sets.value.filter((set) => {
    if (props.image === 'missing' && set.logoUrl) return false
    if (!term) return true
    return set.name.toLowerCase().includes(term) || set.code.toLowerCase().includes(term)
  })
})

const setPages = computed(() => Math.max(Math.ceil(matchingSets.value.length / SETS_PER_PAGE), 1))
const shownSets = computed(() =>
  matchingSets.value.slice((currentPage.value - 1) * SETS_PER_PAGE, currentPage.value * SETS_PER_PAGE),
)

let latestCards = 0

async function loadCards() {
  const request = ++latestCards
  loading.value = true
  try {
    const result = await adminApi.imageCards({
      q: props.q,
      game: props.game,
      set: props.set,
      image: props.image,
      sort: props.sort,
      page: currentPage.value - 1,
    })
    if (request === latestCards) cards.value = result
  } catch (exception) {
    if (request === latestCards) error.value = exception.message
  } finally {
    if (request === latestCards) loading.value = false
  }
}

let latestSets = 0

async function loadSets() {
  const game = setGame.value
  if (!game) {
    sets.value = []
    return
  }
  const request = ++latestSets
  try {
    const result = await catalogApi.sets(game)
    if (request === latestSets) sets.value = result
  } catch (exception) {
    if (request === latestSets) error.value = exception.message
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

/** Keeps the row where it is, also when it no longer matches the filter, so the list doesn't jump while working. */
function uploadCard(card, event) {
  const file = chosenFile(event)
  if (!file) return
  change(`card-${card.id}`, async () => Object.assign(card, toCard(await adminApi.uploadCardImage(card.id, file))), t('admin.images.saved'))
}

function resetCard(card) {
  change(`card-${card.id}`, async () => Object.assign(card, toCard(await adminApi.resetCardImage(card.id))), t('admin.images.resetDone'))
}

function uploadSet(set, event) {
  const file = chosenFile(event)
  if (!file) return
  change(`set-${set.code}`, async () => Object.assign(set, toSet(await adminApi.uploadSetLogo(set.game, set.code, file))), t('admin.images.saved'))
}

function resetSet(set) {
  change(`set-${set.code}`, async () => Object.assign(set, toSet(await adminApi.resetSetLogo(set.game, set.code))), t('admin.images.resetDone'))
}

const toCard = (image) => ({ imageUrl: image.imageUrl, customImage: image.custom })
const toSet = (image) => ({ logoUrl: image.imageUrl, customLogo: image.custom })

let searchTimer = null

/** A search for a card means nothing in the list of sets, so the other tab starts fresh. */
function switchTo(kind) {
  clearTimeout(searchTimer)
  searchText.value = ''
  clearMessages()
  update({ kind, q: '', set: '', image: '', sort: '' })
}

watch(searchText, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => update({ q: value.trim() }), SEARCH_DELAY_MS)
})

watch(() => props.q, (value) => {
  if (value !== searchText.value.trim()) searchText.value = value
})

watch(
  () => [props.kind, props.q, props.game, props.set, props.image, props.sort, currentPage.value],
  () => {
    if (!showingSets.value) loadCards()
  },
  { immediate: true },
)

watch([() => props.kind, setGame], loadSets, { immediate: true })
</script>

<template>
  <h1>{{ t('admin.images.title') }}</h1>
  <p class="intro">{{ t('admin.images.intro') }}</p>
  <p class="intro">{{ t('admin.images.rights') }}</p>

  <div class="kinds" role="group" :aria-label="t('admin.images.kind')">
    <button class="kind" type="button" :aria-pressed="!showingSets" @click="switchTo('cards')">
      {{ t('admin.images.cards') }}
    </button>
    <button class="kind" type="button" :aria-pressed="showingSets" @click="switchTo('sets')">
      {{ t('admin.images.sets') }}
    </button>
  </div>

  <div class="filters">
    <div class="field grow">
      <label class="label" :for="ids.search">{{ t('admin.images.search') }}</label>
      <input :id="ids.search" v-model="searchText" class="input" type="search" autocomplete="off" />
    </div>

    <div class="field">
      <label class="label" :for="ids.game">{{ t('admin.images.game') }}</label>
      <select :id="ids.game" v-model="gameFilter" class="input">
        <option v-if="!showingSets" value="">{{ t('admin.images.allGames') }}</option>
        <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
      </select>
    </div>

    <template v-if="!showingSets">
      <div class="field">
        <label class="label" :for="ids.set">{{ t('admin.images.set') }}</label>
        <select :id="ids.set" v-model="setFilter" class="input" :disabled="!game">
          <option value="">{{ game ? t('admin.images.allSets') : t('admin.images.chooseGameFirst') }}</option>
          <option v-for="item in sets" :key="item.code" :value="item.code">{{ item.name }} ({{ item.code }})</option>
        </select>
      </div>

      <div class="field">
        <label class="label" :for="ids.image">{{ t('admin.images.image') }}</label>
        <select :id="ids.image" v-model="imageFilter" class="input">
          <option value="">{{ t('admin.images.allCards') }}</option>
          <option value="missing">{{ t('admin.images.missing') }}</option>
          <option value="custom">{{ t('admin.images.withCustom') }}</option>
        </select>
      </div>

      <div class="field">
        <label class="label" :for="ids.sort">{{ t('admin.images.sort') }}</label>
        <select :id="ids.sort" v-model="sortFilter" class="input">
          <option value="">{{ t('admin.images.sortSet') }}</option>
          <option value="name">{{ t('admin.images.sortName') }}</option>
          <option value="pokedex">{{ t('admin.images.sortPokedex') }}</option>
        </select>
      </div>
    </template>

    <label v-else class="check">
      <input
        type="checkbox"
        :checked="image === 'missing'"
        @change="update({ image: $event.target.checked ? 'missing' : '' })"
      />
      {{ t('admin.images.onlyWithoutLogo') }}
    </label>

    <button v-if="hasFilters" class="text-button clear" type="button" @click="update({ q: '', game: '', set: '', image: '' })">
      {{ t('admin.images.clear') }}
    </button>
  </div>

  <p v-if="!showingSets && image === 'missing'" class="hint">{{ t('admin.images.missingHint') }}</p>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <FormMessage v-if="message" class="message" type="success">{{ message }}</FormMessage>

  <template v-if="!showingSets">
    <p v-if="cards && !loading" class="count" role="status">
      {{ countLabel(cards.totalItems, 'admin.images.oneCard', 'admin.images.cardCount') }}
    </p>
    <p v-if="loading && !cards" class="hint" role="status">{{ t('admin.images.loading') }}</p>
    <p v-else-if="cards && !cards.items.length" class="hint">{{ t('admin.images.noResults') }}</p>

    <ul v-else-if="cards" class="items" :aria-busy="loading">
      <li v-for="card in cards.items" :key="card.id" class="item">
        <CardImage class="thumb" :src="card.imageUrl" :alt="card.name" />
        <div class="text">
          <p class="name">{{ card.name }}</p>
          <p class="details">{{ cardDetails(card) }}</p>
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
    <PageLinks v-if="cards" :page="currentPage" :total-pages="cards.totalPages" />
  </template>

  <template v-else>
    <p class="count" role="status">{{ countLabel(matchingSets.length, 'admin.images.oneSet', 'admin.images.setCount') }}</p>
    <p v-if="!shownSets.length" class="hint">{{ t('admin.images.noResults') }}</p>

    <ul v-else class="items">
      <li v-for="item in shownSets" :key="item.code" class="item">
        <span class="logo-box">
          <img v-if="item.logoUrl" class="logo" :src="item.logoUrl" alt="" loading="lazy" decoding="async" />
          <span v-else class="code">{{ item.code }}</span>
        </span>
        <div class="text">
          <p class="name">{{ item.name }}</p>
          <p class="details">{{ item.code }}</p>
          <p v-if="item.customLogo" class="custom">{{ t('admin.images.custom') }}</p>
        </div>
        <div class="actions">
          <label class="btn btn-secondary action" :class="{ busy: busyItem === `set-${item.code}` }">
            <Upload :size="16" aria-hidden="true" />
            {{ t('admin.images.choose') }}
            <input
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :aria-label="t('admin.images.chooseLabel', { name: item.name })"
              :disabled="busyItem === `set-${item.code}`"
              @change="uploadSet(item, $event)"
            />
          </label>
          <button
            v-if="item.customLogo"
            class="text-button"
            type="button"
            :aria-label="t('admin.images.resetLabel', { name: item.name })"
            :disabled="busyItem === `set-${item.code}`"
            @click="resetSet(item)"
          >
            <RotateCcw :size="16" aria-hidden="true" />
            {{ t('admin.images.reset') }}
          </button>
        </div>
      </li>
    </ul>
    <PageLinks :page="currentPage" :total-pages="setPages" />
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

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
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
  flex: 2 1 14rem;
  max-width: 22rem;
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

.input:disabled {
  color: var(--color-ink-soft);
}

.check {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.check input {
  width: 1.125rem;
  height: 1.125rem;
  margin: 0;
  accent-color: var(--color-brand);
}

.message {
  margin-top: var(--space-3);
}

.hint,
.count {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.items {
  margin: var(--space-3) 0 0;
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
