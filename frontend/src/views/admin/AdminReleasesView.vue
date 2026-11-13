<script setup>
import { reactive, ref, useId, watch } from 'vue'
import { GalleryHorizontal, Plus, RefreshCw, Sparkles, Upload, X } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES, findGame } from '../../data/games'
import { adminContentApi, adminReleaseSuggestionApi, adminShopApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const SEARCH_DELAY_MS = 250

const uid = useId()
const field = (name) => `${uid}-${name}`

const releases = ref([])
const editingId = ref(null)
const formOpen = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blank = () => ({
  game: 'pokemon',
  name: '',
  releaseDate: '',
  tentative: false,
  description: '',
  setCode: '',
  published: true,
  products: [],
})
const form = reactive(blank())

async function load() {
  try {
    releases.value = await adminContentApi.releases()
  } catch (exception) {
    error.value = exception.message
  }
}

/** Upcoming sets the card catalog and TCGplayer know. Adding one opens the form, filled in. */
const suggestions = ref([])
const refreshing = ref(false)

async function loadSuggestions() {
  try {
    suggestions.value = await adminReleaseSuggestionApi.list()
  } catch (exception) {
    error.value = exception.message
  }
}

async function refreshSuggestions() {
  refreshing.value = true
  error.value = ''
  try {
    suggestions.value = await adminReleaseSuggestionApi.refresh()
  } catch (exception) {
    error.value = exception.message
  } finally {
    refreshing.value = false
  }
}

function addSuggestion(suggestion) {
  startNew()
  Object.assign(form, { game: suggestion.game, name: suggestion.name, releaseDate: suggestion.releaseDate, setCode: suggestion.setCode ?? '' })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function dismissSuggestion(suggestion) {
  error.value = ''
  try {
    await adminReleaseSuggestionApi.dismiss(suggestion.id)
    suggestions.value = suggestions.value.filter((item) => item.id !== suggestion.id)
  } catch (exception) {
    error.value = exception.message
  }
}

function startNew() {
  Object.assign(form, blank())
  editingId.value = null
  formOpen.value = true
  message.value = ''
}

function startEdit(release) {
  Object.assign(form, {
    game: release.game,
    name: release.name,
    releaseDate: release.releaseDate,
    tentative: release.tentative,
    description: release.description ?? '',
    setCode: release.setCode ?? '',
    published: release.published,
    products: release.products.map((product) => ({ id: product.id, name: product.name })),
  })
  editingId.value = release.id
  formOpen.value = true
  message.value = ''
}

async function run(action, done) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await action()
    await load()
    if (done) message.value = done
    return result ?? true
  } catch (exception) {
    error.value = exception.message
    return null
  } finally {
    busy.value = false
  }
}

function save() {
  const body = {
    game: form.game,
    name: form.name,
    releaseDate: form.releaseDate || null,
    tentative: form.tentative,
    description: form.description || null,
    setCode: form.setCode || null,
    published: form.published,
    productIds: form.products.map((product) => product.id),
  }
  run(async () => {
    if (editingId.value) await adminContentApi.updateRelease(editingId.value, body)
    else await adminContentApi.createRelease(body)
    formOpen.value = false
    loadSuggestions()
  }, t('admin.releases.saved'))
}

function remove(release) {
  if (!window.confirm(t('admin.releases.deleteConfirm', { name: release.name }))) return
  run(() => adminContentApi.deleteRelease(release.id))
}

function uploadImage(release, event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    error.value = t('admin.images.tooLarge')
    return
  }
  run(() => adminContentApi.uploadReleaseImage(release.id, file))
}

function makeSlide(release) {
  run(() => adminContentApi.releaseSlide(release.id), t('admin.releases.slideMade'))
}

/** Pre-orders to link: a search over the shop's products. */
const query = ref('')
const results = ref([])
let timer = null

watch(query, (value) => {
  clearTimeout(timer)
  if (value.trim().length < 2) {
    results.value = []
    return
  }
  timer = setTimeout(async () => {
    try {
      const page = await adminShopApi.products({ q: value.trim(), size: 8 })
      const chosen = new Set(form.products.map((product) => product.id))
      results.value = page.items.filter((item) => !chosen.has(item.id) && item.status !== 'ARCHIVED')
    } catch (exception) {
      error.value = exception.message
    }
  }, SEARCH_DELAY_MS)
})

function addProduct(item) {
  form.products.push({ id: item.id, name: item.name })
  query.value = ''
  results.value = []
}

load()
loadSuggestions()
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.releases.title') }}</h1>
      <p class="admin-intro">{{ t('admin.releases.intro') }}</p>
    </div>
    <div class="head-actions">
      <router-link class="btn btn-secondary btn-small" to="/releasekalender" target="_blank">{{ t('admin.releases.view') }}</router-link>
      <button class="btn btn-primary btn-small" type="button" @click="startNew">
        <Plus :size="18" aria-hidden="true" />
        {{ t('admin.releases.add') }}
      </button>
    </div>
  </div>

  <FormMessage v-if="message" type="success" class="message">
    {{ message }}
    <router-link v-if="message === t('admin.releases.slideMade')" to="/admin/hero">{{ t('admin.releases.toSlides') }}</router-link>
  </FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form v-if="formOpen" class="admin-panel" @submit.prevent="save">
    <h2>{{ editingId ? t('admin.releases.edit') : t('admin.releases.add') }}</h2>
    <div class="admin-fields">
      <div class="admin-field">
        <label class="admin-label" :for="field('game')">{{ t('admin.releases.game') }}</label>
        <select :id="field('game')" v-model="form.game" class="admin-input">
          <option v-for="game in GAMES" :key="game.slug" :value="game.slug">{{ t(game.labelKey) }}</option>
        </select>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('name')">{{ t('admin.releases.name') }}</label>
        <input :id="field('name')" v-model="form.name" class="admin-input" required maxlength="150" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('date')">{{ t('admin.releases.date') }}</label>
        <input :id="field('date')" v-model="form.releaseDate" class="admin-input" type="date" required />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('set')">{{ t('admin.releases.setCode') }}</label>
        <input :id="field('set')" v-model="form.setCode" class="admin-input" maxlength="50" autocomplete="off" />
        <p class="admin-hint">{{ t('admin.releases.setCodeHint') }}</p>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('description')">{{ t('admin.releases.description') }}</label>
        <textarea :id="field('description')" v-model="form.description" class="admin-input short" maxlength="1000"></textarea>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('search')">{{ t('admin.releases.products') }}</label>
        <ul v-if="form.products.length" class="chosen">
          <li v-for="(product, index) in form.products" :key="product.id">
            {{ product.name }}
            <button
              class="remove"
              type="button"
              :aria-label="t('admin.releases.removeProduct', { name: product.name })"
              @click="form.products.splice(index, 1)"
            >
              <X :size="14" aria-hidden="true" />
            </button>
          </li>
        </ul>
        <input :id="field('search')" v-model="query" class="admin-input" type="search" autocomplete="off" :placeholder="t('admin.releases.search')" />
        <ul v-if="results.length" class="results">
          <li v-for="item in results" :key="item.id">
            <button class="link-button" type="button" @click="addProduct(item)">
              {{ item.name }}<span v-if="item.preOrder" class="admin-hint"> ({{ t('shop.badges.preOrder') }})</span>
            </button>
          </li>
        </ul>
        <p class="admin-hint">{{ t('admin.releases.productsHint') }}</p>
      </div>
      <label class="admin-check">
        <input v-model="form.tentative" type="checkbox" />
        {{ t('admin.releases.tentative') }}
      </label>
      <label class="admin-check">
        <input v-model="form.published" type="checkbox" />
        {{ t('admin.releases.published') }}
      </label>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.releases.save') }}</button>
      <button class="link-button" type="button" @click="formOpen = false">{{ t('admin.releases.cancel') }}</button>
    </div>
  </form>

  <section class="admin-panel suggestions" aria-labelledby="release-suggestions">
    <div class="suggestions-head">
      <h2 id="release-suggestions"><Sparkles :size="18" aria-hidden="true" />{{ t('admin.releases.suggestions.title') }}</h2>
      <button class="link-button" type="button" :disabled="refreshing" @click="refreshSuggestions">
        <RefreshCw :size="14" aria-hidden="true" />
        {{ refreshing ? t('admin.releases.suggestions.refreshing') : t('admin.releases.suggestions.refresh') }}
      </button>
    </div>
    <p class="admin-hint">{{ t('admin.releases.suggestions.intro') }}</p>
    <ul v-if="suggestions.length" class="suggestion-list">
      <li v-for="suggestion in suggestions" :key="suggestion.id" class="suggestion">
        <div>
          <p class="name">{{ suggestion.name }}</p>
          <p class="admin-hint">
            {{ t(findGame(suggestion.game)?.labelKey ?? '') }}, {{ formatDate(suggestion.releaseDate) }} ·
            {{ t(`admin.releases.suggestions.sources.${suggestion.source}`) }}
          </p>
        </div>
        <div class="actions">
          <button class="btn btn-secondary btn-small" type="button" @click="addSuggestion(suggestion)">{{ t('admin.releases.suggestions.add') }}</button>
          <button class="link-button" type="button" @click="dismissSuggestion(suggestion)">{{ t('admin.releases.suggestions.dismiss') }}</button>
        </div>
      </li>
    </ul>
    <p v-else class="admin-hint">{{ t('admin.releases.suggestions.empty') }}</p>
  </section>

  <ul class="releases">
    <li v-for="release in releases" :key="release.id" class="release" :data-game="findGame(release.game)?.accent">
      <span class="swatch">
        <img v-if="release.imageUrl" :src="release.imageUrl" alt="" loading="lazy" decoding="async" />
      </span>
      <div class="text">
        <p class="name">{{ release.name }}</p>
        <p class="admin-hint">
          {{ t(findGame(release.game)?.labelKey ?? '') }},
          {{ release.tentative ? t('admin.releases.expected', { date: formatDate(release.releaseDate) }) : formatDate(release.releaseDate) }}
          <span v-if="!release.published" class="hidden-state">{{ t('admin.releases.hidden') }}</span>
        </p>
        <p class="admin-hint">
          {{ release.products.length ? t('admin.releases.productCount', { count: release.products.length }) : release.setName ? t('admin.releases.fromSet', { set: release.setName }) : t('admin.releases.noProducts') }}
        </p>
        <div class="actions">
          <button class="link-button" type="button" @click="startEdit(release)">{{ t('admin.releases.edit') }}</button>
          <label class="link-button upload">
            <Upload :size="14" aria-hidden="true" />
            {{ t('admin.releases.chooseImage') }}
            <input class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadImage(release, $event)" />
          </label>
          <button v-if="release.imageUrl" class="link-button" type="button" @click="run(() => adminContentApi.removeReleaseImage(release.id))">
            {{ t('admin.releases.removeImage') }}
          </button>
          <button class="link-button" type="button" :disabled="busy" @click="makeSlide(release)">
            <GalleryHorizontal :size="14" aria-hidden="true" />
            {{ t('admin.releases.makeSlide') }}
          </button>
          <button class="link-button danger" type="button" @click="remove(release)">{{ t('admin.releases.delete') }}</button>
        </div>
      </div>
    </li>
  </ul>
  <p v-if="!releases.length && !formOpen" class="admin-intro">{{ t('admin.releases.empty') }}</p>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.suggestions {
  margin-top: var(--space-4);
}

.suggestions-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  align-items: center;
  justify-content: space-between;
}

.suggestions-head h2 {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-size: var(--text-lg);
}

.suggestion-list {
  display: grid;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.suggestion {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) 0;
  border-top: 1px solid var(--color-line);
}

.suggestion .name {
  font-weight: 700;
}

.suggestion .actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  align-items: center;
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.message {
  margin-top: var(--space-3);
}

.admin-panel {
  max-width: 60rem;
}

textarea.short {
  min-height: 5rem;
}

.chosen {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0 0 var(--space-2);
  padding: 0;
  list-style: none;
}

.chosen li {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-1) var(--space-1) var(--space-3);
  border-radius: var(--radius-round);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

.remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.remove:hover {
  background: var(--color-tint-strong);
}

.results {
  margin: var(--space-1) 0 0;
  padding: var(--space-2);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  list-style: none;
}

.releases {
  display: grid;
  gap: var(--space-3);
  max-width: 60rem;
  margin: var(--space-5) 0 var(--space-3);
  padding: 0;
  list-style: none;
}

.release {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-left: 5px solid var(--accent);
  border-radius: var(--radius-md);
}

.swatch {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 4rem;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--color-tint);
}

.swatch img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.text {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.name {
  font-weight: 700;
}

.hidden-state {
  margin-left: var(--space-2);
  color: var(--color-warning);
  font-weight: 700;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 var(--space-3);
}

.upload,
.actions .link-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.upload:focus-within {
  outline: 3px solid var(--color-focus);
  outline-offset: 2px;
}
</style>
