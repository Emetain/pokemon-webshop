<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { findGame } from '../../data/games'
import { adminApi } from '../../services/api'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const POLL_INTERVAL_MS = 3000

const games = ref([])
const loading = ref(true)
const error = ref('')
const starting = ref(null)

let pollTimer = null
let stopped = false

const anyRunning = computed(() => games.value.some(isRunning))

function isRunning(game) {
  return game.lastRun?.status === 'RUNNING'
}

function gameLabel(slug) {
  return t(findGame(slug).labelKey)
}

function runFacts(run) {
  return [
    ['processedSets', run.setsProcessed],
    ['created', run.cardsCreated],
    ['updated', run.cardsUpdated],
    ['unchanged', run.cardsUnchanged],
    ['failed', run.cardsFailed],
    ['storedPrices', run.pricesStored],
  ]
}

async function load() {
  clearTimeout(pollTimer)
  try {
    games.value = await adminApi.syncStatus()
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
  if (anyRunning.value && !stopped) pollTimer = setTimeout(load, POLL_INTERVAL_MS)
}

async function start(game) {
  starting.value = game
  error.value = ''
  try {
    await adminApi.startSync(game)
  } catch (exception) {
    error.value = exception.message
  }
  await load()
  starting.value = null
}

onMounted(load)

onBeforeUnmount(() => {
  stopped = true
  clearTimeout(pollTimer)
})
</script>

<template>
  <h1>{{ t('admin.sync.title') }}</h1>
  <p class="intro">{{ t('admin.sync.intro') }}</p>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <p v-if="loading" class="intro" role="status">{{ t('admin.sync.loading') }}</p>

  <ul v-else class="games">
    <li v-for="game in games" :key="game.game" class="game" :data-game="findGame(game.game).accent">
      <div class="game-head">
        <h2>{{ gameLabel(game.game) }}</h2>
        <span v-if="game.lastRun" class="status" :class="game.lastRun.status.toLowerCase()">
          {{ t(`admin.sync.status.${game.lastRun.status}`) }}
        </span>
      </div>

      <h3 class="group">{{ t('admin.sync.inDatabase') }}</h3>
      <dl class="numbers">
        <div>
          <dt>{{ t('admin.sync.sets') }}</dt>
          <dd>{{ formatNumber(game.setCount) }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.sync.cards') }}</dt>
          <dd>{{ formatNumber(game.cardCount) }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.sync.prices') }}</dt>
          <dd>{{ formatNumber(game.priceCount) }}</dd>
        </div>
        <div v-if="game.speciesCount !== null">
          <dt>{{ t('admin.sync.species') }}</dt>
          <dd>{{ formatNumber(game.speciesCount) }}</dd>
        </div>
      </dl>

      <h3 class="group">{{ t('admin.sync.lastRun') }}</h3>
      <template v-if="game.lastRun">
        <dl class="times">
          <div>
            <dt>{{ t('admin.sync.started') }}</dt>
            <dd>{{ formatDateTime(game.lastRun.startedAt) }}</dd>
          </div>
          <div v-if="game.lastRun.finishedAt">
            <dt>{{ t('admin.sync.finished') }}</dt>
            <dd>{{ formatDateTime(game.lastRun.finishedAt) }}</dd>
          </div>
        </dl>
        <dl class="numbers small">
          <div v-for="[key, value] in runFacts(game.lastRun)" :key="key">
            <dt>{{ t(`admin.sync.${key}`) }}</dt>
            <dd>{{ formatNumber(value) }}</dd>
          </div>
        </dl>
        <FormMessage v-if="game.lastRun.error" class="message">{{ game.lastRun.error }}</FormMessage>
      </template>
      <p v-else class="never">{{ t('admin.sync.neverRun') }}</p>

      <button
        class="btn btn-primary start"
        type="button"
        :disabled="isRunning(game) || starting === game.game"
        @click="start(game.game)"
      >
        <RefreshCw :size="18" aria-hidden="true" />
        {{ isRunning(game) ? t('admin.sync.running') : t('admin.sync.start') }}
      </button>
    </li>
  </ul>
</template>

<style scoped>
.intro {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}

.message {
  margin-top: var(--space-3);
  overflow-wrap: anywhere;
}

.games {
  display: grid;
  gap: var(--space-4);
  margin: var(--space-5) 0 0;
  padding: 0;
  list-style: none;
}

.game {
  display: flex;
  flex-direction: column;
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-top: 3px solid var(--accent);
  border-radius: var(--radius-md);
}

.game-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.status {
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 650;
}

.status.running {
  background: var(--color-tint-strong);
  color: var(--color-brand-deep);
}

.status.completed {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.status.failed {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.group {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 650;
}

.numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: var(--space-2) 0 0;
}

.numbers dt,
.times dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.numbers dd {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
}

.numbers.small dd {
  font-size: var(--text-md);
}

.times {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-5);
  margin: var(--space-2) 0 0;
}

.times dd {
  margin: 0;
  font-size: var(--text-sm);
}

.never {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.start {
  margin-top: var(--space-4);
}

@media (min-width: 1100px) {
  .games {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .start {
    align-self: flex-start;
    margin-top: auto;
  }

  .game > :nth-last-child(2) {
    margin-bottom: var(--space-4);
  }
}
</style>
