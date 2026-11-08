<script setup>
import { ref, useId, watch } from 'vue'
import { Download, Upload } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { LANGUAGES, languageLabel } from '../../data/shop'
import { adminShopApi, catalogApi } from '../../services/api'
import { formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const MAX_FILE_BYTES = 5 * 1024 * 1024

const uid = useId()
const field = (name) => `${uid}-${name}`

// The product list of Cardmarket.
const importGame = ref('pokemon')
const importing = ref(false)
const importResult = ref(null)
const importError = ref('')

async function startImport() {
  importing.value = true
  importError.value = ''
  importResult.value = null
  try {
    importResult.value = await adminShopApi.importCardmarket(importGame.value)
  } catch (exception) {
    importError.value = exception.message
  } finally {
    importing.value = false
  }
}

// The usual products of one set.
const setGame = ref('pokemon')
const sets = ref([])
const setCode = ref('')
const setLanguage = ref('EN')
const templates = ref([])
const chosenTypes = ref([])
const setBusy = ref(false)
const setResult = ref(null)
const setError = ref('')

async function loadSetOptions() {
  setCode.value = ''
  setResult.value = null
  try {
    const [setList, templateList] = await Promise.all([
      catalogApi.sets(setGame.value),
      adminShopApi.setTemplates(setGame.value),
    ])
    sets.value = setList
    templates.value = templateList
    chosenTypes.value = templateList.filter((template) => template.usual).map((template) => template.type)
  } catch (exception) {
    setError.value = exception.message
  }
}

async function createForSet() {
  setBusy.value = true
  setError.value = ''
  setResult.value = null
  try {
    setResult.value = await adminShopApi.createForSet({
      game: setGame.value,
      setCode: setCode.value,
      language: setLanguage.value,
      types: chosenTypes.value,
    })
  } catch (exception) {
    setError.value = exception.message
  } finally {
    setBusy.value = false
  }
}

// Spreadsheet.
const csvBusy = ref(false)
const csvResult = ref(null)
const csvError = ref('')

async function uploadCsv(event) {
  const [file] = event.target.files
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    csvError.value = t('admin.images.tooLarge')
    return
  }
  csvBusy.value = true
  csvError.value = ''
  csvResult.value = null
  try {
    csvResult.value = await adminShopApi.importCsv(file)
  } catch (exception) {
    csvError.value = exception.message
  } finally {
    csvBusy.value = false
  }
}

watch(setGame, loadSetOptions, { immediate: true })
</script>

<template>
  <h1>{{ t('admin.import.title') }}</h1>
  <p class="admin-intro">{{ t('admin.import.intro') }}</p>

  <section class="admin-panel" aria-labelledby="import-cardmarket">
    <h2 id="import-cardmarket">{{ t('admin.import.cardmarketTitle') }}</h2>
    <p class="admin-intro">{{ t('admin.import.cardmarketIntro') }}</p>
    <p class="admin-intro legal">{{ t('admin.import.legal') }}</p>
    <div class="admin-fields">
      <div class="admin-field">
        <label class="admin-label" :for="field('import-game')">{{ t('admin.import.game') }}</label>
        <select :id="field('import-game')" v-model="importGame" class="admin-input" :disabled="importing">
          <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
        </select>
      </div>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="button" :disabled="importing" @click="startImport">
        {{ importing ? t('admin.import.running') : t('admin.import.start') }}
      </button>
    </div>
    <div v-if="importResult" class="result" role="status">
      <FormMessage type="success">
        {{
          t('admin.import.result', {
            created: formatNumber(importResult.created),
            updated: formatNumber(importResult.updated),
            unchanged: formatNumber(importResult.unchanged),
          })
        }}
      </FormMessage>
      <p v-if="importResult.keptEdited" class="admin-hint">
        {{ t('admin.import.resultKept', { count: formatNumber(importResult.keptEdited) }) }}
      </p>
      <p class="admin-hint">
        {{
          t('admin.import.resultSkipped', {
            type: formatNumber(importResult.skippedType),
            market: formatNumber(importResult.skippedMarket),
            noSet: formatNumber(importResult.skippedNoSet),
          })
        }}
      </p>
    </div>
    <FormMessage v-if="importError" class="result">{{ importError }}</FormMessage>
  </section>

  <section class="admin-panel" aria-labelledby="import-set">
    <h2 id="import-set">{{ t('admin.import.setTitle') }}</h2>
    <p class="admin-intro">{{ t('admin.import.setIntro') }}</p>
    <form @submit.prevent="createForSet">
      <div class="admin-fields">
        <div class="admin-field">
          <label class="admin-label" :for="field('set-game')">{{ t('admin.import.game') }}</label>
          <select :id="field('set-game')" v-model="setGame" class="admin-input">
            <option v-for="item in GAMES" :key="item.slug" :value="item.slug">{{ t(item.labelKey) }}</option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('set')">{{ t('admin.import.set') }}</label>
          <select :id="field('set')" v-model="setCode" class="admin-input" required>
            <option value="" disabled>{{ t('admin.import.chooseSet') }}</option>
            <option v-for="set in sets" :key="set.code" :value="set.code">{{ set.name }} ({{ set.code }})</option>
          </select>
        </div>
        <div class="admin-field">
          <label class="admin-label" :for="field('language')">{{ t('admin.import.language') }}</label>
          <select :id="field('language')" v-model="setLanguage" class="admin-input">
            <option v-for="code in LANGUAGES" :key="code" :value="code">{{ languageLabel(code) }}</option>
          </select>
        </div>
      </div>
      <fieldset class="types">
        <legend class="admin-label">{{ t('admin.import.types') }}</legend>
        <label v-for="template in templates" :key="template.type" class="admin-check">
          <input v-model="chosenTypes" type="checkbox" :value="template.type" />
          {{ template.label }}
        </label>
      </fieldset>
      <div class="admin-actions">
        <button class="btn btn-primary btn-small" type="submit" :disabled="setBusy || !setCode || !chosenTypes.length">
          {{ t('admin.import.setSubmit') }}
        </button>
      </div>
    </form>
    <div v-if="setResult" class="result" role="status">
      <FormMessage type="success">
        {{
          setResult.created.length
            ? t('admin.import.setResult', { count: setResult.created.length })
            : t('admin.import.setNothing')
        }}
      </FormMessage>
      <ul v-if="setResult.created.length" class="created">
        <li v-for="row in setResult.created" :key="row.id">
          <router-link :to="`/admin/producten/${row.id}`">{{ row.name }}</router-link>
        </li>
      </ul>
    </div>
    <FormMessage v-if="setError" class="result">{{ setError }}</FormMessage>
  </section>

  <section class="admin-panel" aria-labelledby="import-csv">
    <h2 id="import-csv">{{ t('admin.import.csvTitle') }}</h2>
    <p class="admin-intro">{{ t('admin.import.csvIntro') }}</p>
    <div class="admin-actions">
      <a class="btn btn-secondary btn-small" :href="adminShopApi.csvUrl" download>
        <Download :size="18" aria-hidden="true" />
        {{ t('admin.import.download') }}
      </a>
      <label class="btn btn-secondary btn-small upload" :class="{ busy: csvBusy }">
        <Upload :size="18" aria-hidden="true" />
        {{ t('admin.import.upload') }}
        <input class="visually-hidden" type="file" accept=".csv,text/csv" :disabled="csvBusy" @change="uploadCsv" />
      </label>
    </div>
    <div v-if="csvResult" class="result" role="status">
      <FormMessage :type="csvResult.errors.length ? 'error' : 'success'">
        {{
          t('admin.import.csvResult', {
            created: csvResult.created,
            updated: csvResult.updated,
            unchanged: csvResult.unchanged,
            stock: csvResult.stockChanges,
          })
        }}
      </FormMessage>
      <template v-if="csvResult.errors.length">
        <p class="admin-hint">{{ t('admin.import.csvErrors') }}</p>
        <ul class="errors">
          <li v-for="lineError in csvResult.errors" :key="lineError.line">
            {{ t('admin.import.csvLine', { line: lineError.line, message: lineError.message }) }}
          </li>
        </ul>
      </template>
    </div>
    <FormMessage v-if="csvError" class="result">{{ csvError }}</FormMessage>
  </section>
</template>

<style scoped>
.admin-panel {
  max-width: 60rem;
}

.legal {
  font-size: var(--text-sm);
}

.types {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-5);
  margin: var(--space-3) 0 0;
  padding: 0;
  border: 0;
}

.types legend {
  width: 100%;
}

.result {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.created,
.errors {
  margin: 0;
  padding-left: var(--space-5);
  font-size: var(--text-sm);
}

.upload {
  cursor: pointer;
}

.upload:focus-within {
  outline: 3px solid var(--color-focus);
  outline-offset: 2px;
}

.upload.busy {
  cursor: progress;
  opacity: 0.6;
}
</style>
