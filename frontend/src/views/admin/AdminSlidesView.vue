<script setup>
import { reactive, ref, useId } from 'vue'
import { ArrowDown, ArrowUp, Plus, Upload } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { adminShopApi } from '../../services/api'
import { fromLocalInput, toLocalInput } from '../../utils/admin'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024

const uid = useId()
const field = (name) => `${uid}-${name}`

const slides = ref([])
const editingId = ref(null)
const formOpen = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blank = () => ({
  title: '',
  text: '',
  buttonLabel: '',
  linkUrl: '',
  game: '',
  active: true,
  startsAt: '',
  endsAt: '',
})
const form = reactive(blank())

async function load() {
  try {
    slides.value = await adminShopApi.slides()
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

function startEdit(slide) {
  Object.assign(form, {
    title: slide.title,
    text: slide.text ?? '',
    buttonLabel: slide.buttonLabel ?? '',
    linkUrl: slide.linkUrl ?? '',
    game: slide.game ?? '',
    active: slide.active,
    startsAt: toLocalInput(slide.startsAt),
    endsAt: toLocalInput(slide.endsAt),
  })
  editingId.value = slide.id
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
    game: form.game || null,
    startsAt: fromLocalInput(form.startsAt),
    endsAt: fromLocalInput(form.endsAt),
  }
  run(async () => {
    if (editingId.value) await adminShopApi.updateSlide(editingId.value, body)
    else await adminShopApi.createSlide(body)
    formOpen.value = false
  }, t('admin.slides.saved'))
}

function remove(slide) {
  if (!window.confirm(t('admin.slides.deleteConfirm'))) return
  run(() => adminShopApi.deleteSlide(slide.id))
}

function move(slide, direction) {
  run(() => adminShopApi.moveSlide(slide.id, direction))
}

function uploadImage(slide, event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    error.value = t('admin.images.tooLarge')
    return
  }
  run(() => adminShopApi.uploadSlideImage(slide.id, file))
}

function removeImage(slide) {
  run(() => adminShopApi.removeSlideImage(slide.id))
}

/** Why a slide that is on still isn't showing. */
function stateOf(slide) {
  if (slide.showing) return { kind: 'showing', text: t('admin.slides.showing') }
  if (!slide.active) return { kind: 'off', text: t('admin.slides.off') }
  if (slide.startsAt && new Date(slide.startsAt) > new Date()) return { kind: 'later', text: t('admin.slides.scheduled') }
  return { kind: 'off', text: t('admin.slides.ended') }
}

const gameOf = (slug) => GAMES.find((game) => game.slug === slug)

load()
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.slides.title') }}</h1>
      <p class="admin-intro">{{ t('admin.slides.intro') }}</p>
    </div>
    <button class="btn btn-primary btn-small" type="button" @click="startNew">
      <Plus :size="18" aria-hidden="true" />
      {{ t('admin.slides.add') }}
    </button>
  </div>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form v-if="formOpen" class="admin-panel" @submit.prevent="save">
    <h2>{{ editingId ? t('admin.slides.edit') : t('admin.slides.add') }}</h2>
    <div class="admin-fields">
      <div class="admin-field wide">
        <label class="admin-label" :for="field('title')">{{ t('admin.slides.titleField') }}</label>
        <input :id="field('title')" v-model="form.title" class="admin-input" required maxlength="120" />
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('text')">{{ t('admin.slides.text') }}</label>
        <textarea :id="field('text')" v-model="form.text" class="admin-input short" maxlength="300"></textarea>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('button')">{{ t('admin.slides.buttonLabel') }}</label>
        <input :id="field('button')" v-model="form.buttonLabel" class="admin-input" maxlength="40" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('link')">{{ t('admin.slides.linkUrl') }}</label>
        <input :id="field('link')" v-model="form.linkUrl" class="admin-input" maxlength="300" autocomplete="off" />
        <p class="admin-hint">{{ t('admin.slides.linkHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('game')">{{ t('admin.slides.game') }}</label>
        <select :id="field('game')" v-model="form.game" class="admin-input">
          <option value="">{{ t('admin.slides.brandColor') }}</option>
          <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
        </select>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('starts')">{{ t('admin.slides.startsAt') }}</label>
        <input :id="field('starts')" v-model="form.startsAt" class="admin-input" type="datetime-local" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('ends')">{{ t('admin.slides.endsAt') }}</label>
        <input :id="field('ends')" v-model="form.endsAt" class="admin-input" type="datetime-local" />
        <p class="admin-hint">{{ t('admin.slides.datesHint') }}</p>
      </div>
      <label class="admin-check">
        <input v-model="form.active" type="checkbox" />
        {{ t('admin.slides.active') }}
      </label>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">
        {{ editingId ? t('admin.slides.save') : t('admin.slides.create') }}
      </button>
      <button class="link-button" type="button" @click="formOpen = false">{{ t('admin.slides.cancel') }}</button>
    </div>
  </form>

  <ol class="slides">
    <li class="slide fixed">
      <span class="swatch"></span>
      <div class="slide-text">
        <p class="slide-title">{{ t('admin.slides.binder') }}</p>
      </div>
    </li>
    <li v-for="(slide, index) in slides" :key="slide.id" class="slide">
      <span class="swatch" :data-game="gameOf(slide.game)?.accent">
        <img v-if="slide.imageUrl" :src="slide.imageUrl" alt="" loading="lazy" decoding="async" />
      </span>
      <div class="slide-text">
        <p class="slide-title">{{ slide.title }}</p>
        <p v-if="slide.text" class="admin-hint">{{ slide.text }}</p>
        <p class="admin-hint">
          <span class="state" :class="stateOf(slide).kind">{{ stateOf(slide).text }}</span>
          <span v-if="slide.linkUrl">{{ slide.buttonLabel }}: {{ slide.linkUrl }}</span>
        </p>
        <p v-if="slide.startsAt || slide.endsAt" class="admin-hint">
          <span v-if="slide.startsAt">{{ t('admin.slides.startsAt') }} {{ formatDateTime(slide.startsAt) }}</span>
          <span v-if="slide.endsAt">{{ t('admin.slides.endsAt') }} {{ formatDateTime(slide.endsAt) }}</span>
        </p>
        <div class="slide-actions">
          <button class="link-button" type="button" @click="startEdit(slide)">{{ t('admin.slides.edit') }}</button>
          <label class="link-button upload">
            <Upload :size="14" aria-hidden="true" />
            {{ t('admin.slides.chooseImage') }}
            <input class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadImage(slide, $event)" />
          </label>
          <button v-if="slide.imageUrl" class="link-button" type="button" @click="removeImage(slide)">
            {{ t('admin.slides.removeImage') }}
          </button>
          <button
            class="icon-button"
            type="button"
            :disabled="busy || index === 0"
            :aria-label="t('admin.slides.moveUpLabel', { title: slide.title })"
            @click="move(slide, 'up')"
          >
            <ArrowUp :size="16" aria-hidden="true" />
          </button>
          <button
            class="icon-button"
            type="button"
            :disabled="busy || index === slides.length - 1"
            :aria-label="t('admin.slides.moveDownLabel', { title: slide.title })"
            @click="move(slide, 'down')"
          >
            <ArrowDown :size="16" aria-hidden="true" />
          </button>
          <button class="link-button danger" type="button" @click="remove(slide)">{{ t('admin.slides.delete') }}</button>
        </div>
      </div>
    </li>
  </ol>
  <p v-if="!slides.length" class="admin-intro">{{ t('admin.slides.empty') }}</p>
  <p class="admin-hint">{{ t('admin.slides.imageHint') }}</p>
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
  min-height: 5rem;
}

.slides {
  display: grid;
  gap: var(--space-3);
  max-width: 60rem;
  margin: var(--space-5) 0 var(--space-3);
  padding: 0;
  list-style: none;
}

.slide {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.slide.fixed {
  align-items: center;
  background: var(--color-tint);
}

/* A small version of the slide: its colour, and its image when it has one. */
.swatch {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 4rem;
  padding: var(--space-1);
  border-radius: var(--radius-sm);
  background: var(--accent);
}

.swatch img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.slide-text {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.slide-title {
  font-weight: 700;
  overflow-wrap: anywhere;
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

.slide-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 var(--space-3);
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

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
}

.icon-button:hover:not(:disabled) {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}
</style>
