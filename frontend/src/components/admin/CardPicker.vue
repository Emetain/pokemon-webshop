<script setup>
import { ref, useId, watch } from 'vue'
import CardImage from '../catalog/CardImage.vue'
import { catalogApi } from '../../services/api'
import { t } from '../../utils/i18n'

const SEARCH_DELAY_MS = 350
const MIN_LENGTH = 2

/** Finds a card in the catalog by name. Emits the chosen card as the search API gives it. */
const props = defineProps({
  game: { type: String, default: '' },
  label: { type: String, default: '' },
})

const emit = defineEmits(['choose'])

const id = useId()
const query = ref('')
const results = ref([])
const searching = ref(false)
const error = ref('')

let timer = null
let latestRequest = 0

async function search() {
  const term = query.value.trim()
  const request = ++latestRequest
  if (term.length < MIN_LENGTH) {
    results.value = []
    return
  }
  searching.value = true
  error.value = ''
  try {
    const value = await catalogApi.search({ q: term, game: props.game || undefined, size: 12 })
    if (request === latestRequest) results.value = value.results.items
  } catch (exception) {
    if (request === latestRequest) error.value = exception.message
  } finally {
    if (request === latestRequest) searching.value = false
  }
}

watch(query, () => {
  clearTimeout(timer)
  timer = setTimeout(search, SEARCH_DELAY_MS)
})
watch(() => props.game, search)
</script>

<template>
  <div class="card-picker">
    <label class="admin-label" :for="id">{{ label || t('admin.productForm.cardSearch') }}</label>
    <input :id="id" v-model="query" class="admin-input" type="search" autocomplete="off" />
    <p class="admin-hint">{{ t('admin.productForm.cardSearchHint') }}</p>
    <p v-if="error" class="admin-hint">{{ error }}</p>
    <p v-else-if="!searching && query.trim().length >= 2 && !results.length" class="admin-hint">
      {{ t('admin.single.noResults') }}
    </p>
    <ul v-if="results.length" class="results" :aria-busy="searching">
      <li v-for="card in results" :key="card.id">
        <button class="result" type="button" @click="emit('choose', card)">
          <CardImage class="result-image" :src="card.imageUrl" />
          <span class="result-text">
            <span class="result-name">{{ card.name }}</span>
            <span class="result-meta">{{ card.setName }}, #{{ card.number }}</span>
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.results {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 15rem), 1fr));
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.result {
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  cursor: pointer;
  text-align: left;
}

.result:hover {
  border-color: var(--color-brand);
}

.result-text {
  display: grid;
  min-width: 0;
}

.result-name {
  font-size: var(--text-sm);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.result-meta {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
