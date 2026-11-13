<script setup>
import { reactive, ref, useId } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import MarkupHelp from '../../components/admin/MarkupHelp.vue'
import { adminContentApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** The pages under /info. Each exists with a starting text; the legal ones start as drafts. */
const uid = useId()
const field = (name) => `${uid}-${name}`

const pages = ref([])
const editing = ref(null)
const form = reactive({ title: '', body: '', draft: false })
const busy = ref(false)
const error = ref('')
const message = ref('')

async function load() {
  try {
    pages.value = await adminContentApi.pages()
  } catch (exception) {
    error.value = exception.message
  }
}

function startEdit(page) {
  Object.assign(form, { title: page.title, body: page.body, draft: page.draft })
  editing.value = page.slug
  message.value = ''
}

async function run(action, done) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
    await load()
    message.value = done
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function save() {
  run(async () => {
    await adminContentApi.updatePage(editing.value, form)
    editing.value = null
  }, t('admin.pages.saved'))
}

function reset() {
  if (!window.confirm(t('admin.pages.resetConfirm'))) return
  run(async () => {
    const page = await adminContentApi.resetPage(editing.value)
    Object.assign(form, { title: page.title, body: page.body, draft: page.draft })
  }, t('admin.pages.wasReset'))
}

load()
</script>

<template>
  <h1>{{ t('admin.pages.title') }}</h1>
  <p class="admin-intro">{{ t('admin.pages.intro') }}</p>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form v-if="editing" class="admin-panel" @submit.prevent="save">
    <h2>{{ t('admin.pages.edit', { slug: editing }) }}</h2>
    <div class="admin-fields">
      <div class="admin-field wide">
        <label class="admin-label" :for="field('title')">{{ t('admin.pages.titleField') }}</label>
        <input :id="field('title')" v-model="form.title" class="admin-input" required maxlength="150" />
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('body')">{{ t('admin.pages.body') }}</label>
        <textarea :id="field('body')" v-model="form.body" class="admin-input tall" required maxlength="50000"></textarea>
        <MarkupHelp />
      </div>
      <label class="admin-check">
        <input v-model="form.draft" type="checkbox" />
        {{ t('admin.pages.draft') }}
      </label>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.pages.save') }}</button>
      <button class="link-button" type="button" @click="editing = null">{{ t('admin.pages.cancel') }}</button>
      <button class="link-button danger" type="button" :disabled="busy" @click="reset">{{ t('admin.pages.reset') }}</button>
    </div>
  </form>

  <div class="admin-table-scroll">
    <table class="admin-table">
      <thead>
        <tr>
          <th scope="col">{{ t('admin.pages.page') }}</th>
          <th scope="col">{{ t('admin.pages.state') }}</th>
          <th scope="col">{{ t('admin.pages.updated') }}</th>
          <th scope="col"><span class="visually-hidden">{{ t('admin.pages.actions') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="page in pages" :key="page.slug">
          <th scope="row">
            {{ page.title }}
            <span class="admin-hint block">/info/{{ page.slug }}</span>
          </th>
          <td>
            <span v-if="page.draft" class="draft"><TriangleAlert :size="14" aria-hidden="true" />{{ t('admin.pages.isDraft') }}</span>
            <span v-else class="ready">{{ t('admin.pages.isReady') }}</span>
          </td>
          <td>{{ formatDateTime(page.updatedAt) }}</td>
          <td class="actions">
            <button class="link-button" type="button" @click="startEdit(page)">{{ t('admin.pages.editButton') }}</button>
            <router-link class="link-button" :to="`/info/${page.slug}`" target="_blank">{{ t('admin.pages.view') }}</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.message {
  margin-top: var(--space-3);
}

.admin-panel {
  max-width: 60rem;
}

textarea.tall {
  min-height: 24rem;
  font-family: var(--font-body);
}

.block {
  display: block;
  font-weight: 400;
}

.draft {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-warning);
  font-weight: 700;
}

.ready {
  color: var(--color-success);
  font-weight: 700;
}

.actions {
  white-space: nowrap;
}

.actions .link-button + .link-button {
  margin-left: var(--space-3);
}

.actions a.link-button {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}
</style>
