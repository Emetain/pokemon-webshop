<script setup>
import { reactive, ref, useId } from 'vue'
import { Plus, Upload } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import MarkupHelp from '../../components/admin/MarkupHelp.vue'
import { GAMES, findGame } from '../../data/games'
import { adminContentApi } from '../../services/api'
import { fromLocalInput, toLocalInput } from '../../utils/admin'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024

const uid = useId()
const field = (name) => `${uid}-${name}`

const articles = ref([])
const editingId = ref(null)
const formOpen = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blank = () => ({ title: '', summary: '', body: '', game: '', published: false, publishedAt: '' })
const form = reactive(blank())

async function load() {
  try {
    articles.value = await adminContentApi.news()
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

function startEdit(article) {
  Object.assign(form, {
    title: article.title,
    summary: article.summary ?? '',
    body: article.body,
    game: article.game ?? '',
    published: article.published,
    publishedAt: toLocalInput(article.publishedAt),
  })
  editingId.value = article.id
  formOpen.value = true
  message.value = ''
}

async function run(action, done) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
    await load()
    if (done) message.value = done
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function save() {
  const body = {
    ...form,
    summary: form.summary || null,
    game: form.game || null,
    publishedAt: fromLocalInput(form.publishedAt),
  }
  run(async () => {
    if (editingId.value) await adminContentApi.updateArticle(editingId.value, body)
    else await adminContentApi.createArticle(body)
    formOpen.value = false
  }, t('admin.news.saved'))
}

function remove(article) {
  if (window.confirm(t('admin.news.deleteConfirm', { title: article.title }))) run(() => adminContentApi.deleteArticle(article.id))
}

function uploadImage(article, event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    error.value = t('admin.images.tooLarge')
    return
  }
  run(() => adminContentApi.uploadArticleImage(article.id, file))
}

function stateOf(article) {
  if (!article.published) return { kind: 'off', text: t('admin.news.draft') }
  if (article.publishedAt && new Date(article.publishedAt) > new Date()) {
    return { kind: 'later', text: t('admin.news.planned', { date: formatDateTime(article.publishedAt) }) }
  }
  return { kind: 'showing', text: t('admin.news.published', { date: formatDateTime(article.publishedAt) }) }
}

load()
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.news.title') }}</h1>
      <p class="admin-intro">{{ t('admin.news.intro') }}</p>
    </div>
    <button class="btn btn-primary btn-small" type="button" @click="startNew">
      <Plus :size="18" aria-hidden="true" />
      {{ t('admin.news.add') }}
    </button>
  </div>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form v-if="formOpen" class="admin-panel" @submit.prevent="save">
    <h2>{{ editingId ? t('admin.news.edit') : t('admin.news.add') }}</h2>
    <div class="admin-fields">
      <div class="admin-field wide">
        <label class="admin-label" :for="field('title')">{{ t('admin.news.titleField') }}</label>
        <input :id="field('title')" v-model="form.title" class="admin-input" required maxlength="150" />
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('summary')">{{ t('admin.news.summary') }}</label>
        <textarea :id="field('summary')" v-model="form.summary" class="admin-input short" maxlength="300"></textarea>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('body')">{{ t('admin.news.body') }}</label>
        <textarea :id="field('body')" v-model="form.body" class="admin-input tall" required maxlength="20000"></textarea>
        <MarkupHelp />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('game')">{{ t('admin.news.game') }}</label>
        <select :id="field('game')" v-model="form.game" class="admin-input">
          <option value="">{{ t('admin.news.noGame') }}</option>
          <option v-for="game in GAMES" :key="game.slug" :value="game.slug">{{ t(game.labelKey) }}</option>
        </select>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('date')">{{ t('admin.news.publishedAt') }}</label>
        <input :id="field('date')" v-model="form.publishedAt" class="admin-input" type="datetime-local" />
        <p class="admin-hint">{{ t('admin.news.publishedAtHint') }}</p>
      </div>
      <label class="admin-check">
        <input v-model="form.published" type="checkbox" />
        {{ t('admin.news.publish') }}
      </label>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.news.save') }}</button>
      <button class="link-button" type="button" @click="formOpen = false">{{ t('admin.news.cancel') }}</button>
    </div>
  </form>

  <ul class="articles">
    <li v-for="article in articles" :key="article.id" class="article" :data-game="findGame(article.game)?.accent">
      <span class="swatch">
        <img v-if="article.imageUrl" :src="article.imageUrl" alt="" loading="lazy" decoding="async" />
      </span>
      <div class="text">
        <p class="name">{{ article.title }}</p>
        <p class="admin-hint">
          <span class="state" :class="stateOf(article).kind">{{ stateOf(article).text }}</span>
          <span v-if="article.game">{{ t(findGame(article.game)?.labelKey ?? '') }}</span>
        </p>
        <div class="actions">
          <button class="link-button" type="button" @click="startEdit(article)">{{ t('admin.news.edit') }}</button>
          <router-link class="link-button" :to="`/nieuws/${article.slug}`" target="_blank">{{ t('admin.news.preview') }}</router-link>
          <label class="link-button upload">
            <Upload :size="14" aria-hidden="true" />
            {{ t('admin.news.chooseImage') }}
            <input class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadImage(article, $event)" />
          </label>
          <button v-if="article.imageUrl" class="link-button" type="button" @click="run(() => adminContentApi.removeArticleImage(article.id))">
            {{ t('admin.news.removeImage') }}
          </button>
          <button class="link-button danger" type="button" @click="remove(article)">{{ t('admin.news.delete') }}</button>
        </div>
      </div>
    </li>
  </ul>
  <p v-if="!articles.length && !formOpen" class="admin-intro">{{ t('admin.news.empty') }}</p>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.message {
  margin-top: var(--space-3);
}

.admin-panel {
  max-width: 60rem;
}

textarea.short {
  min-height: 4.5rem;
}

textarea.tall {
  min-height: 18rem;
}

.articles {
  display: grid;
  gap: var(--space-3);
  max-width: 60rem;
  margin: var(--space-5) 0 var(--space-3);
  padding: 0;
  list-style: none;
}

.article {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.swatch {
  height: 4rem;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--accent);
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

.state {
  margin-right: var(--space-2);
  font-weight: 700;
}

.state.showing {
  color: var(--color-success);
}

.state.later {
  color: var(--color-warning);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 var(--space-3);
}

.actions a.link-button {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.upload {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.upload:focus-within {
  outline: 3px solid var(--color-focus);
  outline-offset: 2px;
}
</style>
