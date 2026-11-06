<script setup>
import { computed, onMounted, reactive, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { binderApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const SOURCES = ['MOST_EXPENSIVE_OWNED', 'OWN_SELECTION', 'POKEDEX', 'POPULAR', 'MOST_EXPENSIVE']

const session = useSession()
const id = useId()

const status = reactive({ saved: false, error: '' })
const selection = ref([])
const loadingSelection = ref(false)

const source = computed({
  get: () => session.state.user.binderSource,
  set: async (binderSource) => {
    status.saved = false
    status.error = ''
    try {
      await session.updateProfile({ binderSource })
      status.saved = true
    } catch (exception) {
      status.error = exception.message
    }
  },
})

const showSelection = computed(() => source.value === 'OWN_SELECTION')

async function loadSelection() {
  if (!showSelection.value) return
  loadingSelection.value = true
  try {
    selection.value = await binderApi.selection()
  } catch (exception) {
    status.error = exception.message
  } finally {
    loadingSelection.value = false
  }
}

async function remove(card) {
  status.error = ''
  try {
    await binderApi.deselect(card.id)
    selection.value = selection.value.filter((other) => other.id !== card.id)
  } catch (exception) {
    status.error = exception.message
  }
}

onMounted(loadSelection)
watch(showSelection, loadSelection)
</script>

<template>
  <div class="form-stack">
    <p class="intro">{{ t('account.binder.intro') }}</p>
    <FormMessage v-if="status.error">{{ status.error }}</FormMessage>
    <FormMessage v-if="status.saved" type="success">{{ t('account.binder.saved') }}</FormMessage>

    <div>
      <label class="label" :for="id">{{ t('account.binder.label') }}</label>
      <select :id="id" v-model="source" class="input">
        <option v-for="option in SOURCES" :key="option" :value="option">
          {{ t(`account.binder.sources.${option}`) }}
        </option>
      </select>
    </div>

    <div v-if="showSelection && !loadingSelection">
      <h3 class="selection-title">{{ t('account.binder.selectionTitle') }}</h3>
      <p v-if="!selection.length" class="intro">{{ t('account.binder.selectionEmpty') }}</p>
      <ul v-else class="selection">
        <li v-for="card in selection" :key="card.id" class="selected">
          <router-link :to="{ name: 'card', params: { game: card.game, setCode: card.setCode, cardId: card.id } }">
            {{ card.name }}
          </router-link>
          <button
            class="remove"
            type="button"
            :aria-label="t('account.binder.removeLabel', { name: card.name })"
            @click="remove(card)"
          >
            <X :size="16" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.intro {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.input {
  width: 100%;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.selection-title {
  margin-bottom: var(--space-1);
  font-size: var(--text-md);
}

.selection {
  margin: 0;
  padding: 0;
  list-style: none;
}

.selected {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.remove {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-danger);
  cursor: pointer;
}
</style>
