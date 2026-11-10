<script setup>
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { ChevronLeft, Pause, Play, Square } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { adminDropApi } from '../../services/api'
import { formatDateTime, formatMoney, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * During a drop: the waiting room, sales per product and the buttons to steer it. Afterwards the same page is the
 * report. It refreshes every 5 seconds until the drop has ended.
 */
const props = defineProps({
  id: { type: String, required: true },
})

const REFRESH_MS = 5000

const uid = useId()
const live = ref(null)
const speed = ref('')
const busy = ref(false)
const error = ref('')

const isLive = computed(() => live.value?.status === 'LIVE')
const ended = computed(() => live.value?.status === 'ENDED')

let timer = null

async function load() {
  try {
    const result = await adminDropApi.live(props.id)
    if (!speed.value || speed.value === String(live.value?.queue.admitPerMinute)) speed.value = String(result.queue.admitPerMinute)
    live.value = result
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  }
  clearTimeout(timer)
  if (live.value?.status !== 'ENDED') timer = setTimeout(load, REFRESH_MS)
}

watch(() => props.id, load, { immediate: true })
onBeforeUnmount(() => clearTimeout(timer))

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    live.value = await action()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const togglePause = () => run(() => adminDropApi.steerQueue(props.id, { paused: !live.value.queue.paused }))

function saveSpeed() {
  const value = Number(speed.value)
  run(() => adminDropApi.steerQueue(props.id, { admitPerMinute: value }))
}

function endNow() {
  if (!window.confirm(t('admin.drops.live.endConfirm'))) return
  run(() => adminDropApi.end(props.id))
}

const returnLeftover = () => run(() => adminDropApi.returnLeftover(props.id))

/** "3 min 12 s" for the time to sell out. */
function duration(seconds) {
  if (seconds == null) return t('admin.drops.live.notSoldOut')
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes >= 60) return `${Math.floor(minutes / 60)} u ${minutes % 60} min`
  return minutes ? `${minutes} min ${rest} s` : `${rest} s`
}
</script>

<template>
  <router-link class="back" :to="`/admin/drops/${id}`">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ t('admin.drops.editTitle') }}
  </router-link>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <template v-if="live">
    <div class="head">
      <div>
        <h1>{{ ended ? t('admin.drops.live.reportTitle', { name: live.name }) : t('admin.drops.live.title', { name: live.name }) }}</h1>
        <p class="admin-hint">
          <span class="status" :class="live.status.toLowerCase()">{{ t(`drops.status.${live.status}`) }}</span>
          <template v-if="live.status === 'SCHEDULED'">{{ t('admin.drops.live.notStarted', { date: formatDateTime(live.startsAt) }) }}</template>
          <template v-if="live.startedAt">{{ t('admin.drops.live.started', { date: formatDateTime(live.startedAt) }) }}</template>
          <template v-if="live.endedAt"> · {{ t('admin.drops.live.ended', { date: formatDateTime(live.endedAt) }) }}</template>
          <template v-if="live.endReason"> · {{ live.endReason }}</template>
        </p>
        <p v-if="!ended" class="admin-hint">{{ t('admin.drops.live.refresh') }}</p>
      </div>
      <a class="btn btn-secondary btn-small" :href="`/drops/${live.slug}`" target="_blank" rel="noopener">{{ t('admin.drops.preview') }}</a>
    </div>

    <FormMessage v-if="live.queue.paused && !ended" class="message">{{ t('admin.drops.live.paused') }}</FormMessage>

    <section class="admin-panel">
      <h2>{{ t('admin.drops.live.queue') }}</h2>
      <dl class="stats">
        <div class="stat">
          <dt>{{ t('admin.drops.live.joined') }}</dt>
          <dd>{{ formatNumber(live.queue.joined) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.shuffled') }}</dt>
          <dd>{{ formatNumber(live.queue.shuffled) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.waiting') }}</dt>
          <dd>{{ formatNumber(live.queue.waiting) }}</dd>
          <p class="admin-hint">{{ formatNumber(live.queue.activeWaiting) }} {{ t('admin.drops.live.activeWaiting') }}</p>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.admitted') }}</dt>
          <dd>{{ formatNumber(live.queue.admitted) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.shopping') }}</dt>
          <dd>{{ formatNumber(live.queue.shopping) }}</dd>
        </div>
      </dl>

      <div v-if="!ended" class="controls">
        <form class="speed" @submit.prevent="saveSpeed">
          <label class="admin-label" :for="`${uid}-speed`">{{ t('admin.drops.live.speed') }}</label>
          <div class="speed-row">
            <input :id="`${uid}-speed`" v-model="speed" class="admin-input" type="number" min="1" max="10000" />
            <span class="admin-hint">{{ t('admin.drops.live.perMinute') }}</span>
            <button class="btn btn-secondary btn-small" type="submit" :disabled="busy">{{ t('admin.drops.live.saveSpeed') }}</button>
          </div>
        </form>
        <div class="buttons">
          <button class="btn btn-secondary btn-small" type="button" :disabled="busy" @click="togglePause">
            <component :is="live.queue.paused ? Play : Pause" :size="16" aria-hidden="true" />
            {{ live.queue.paused ? t('admin.drops.live.resume') : t('admin.drops.live.pause') }}
          </button>
          <button v-if="isLive" class="btn btn-small danger" type="button" :disabled="busy" @click="endNow">
            <Square :size="16" aria-hidden="true" />
            {{ t('admin.drops.live.end') }}
          </button>
        </div>
      </div>
    </section>

    <section class="admin-panel">
      <h2>{{ t('admin.drops.live.sales') }}</h2>
      <dl class="stats">
        <div class="stat">
          <dt>{{ t('admin.drops.live.revenue') }}</dt>
          <dd>{{ formatMoney(live.totals.revenueCents, 'EUR') }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.units') }}</dt>
          <dd>{{ formatNumber(live.totals.units) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.orders') }}</dt>
          <dd>{{ formatNumber(live.totals.orders) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.buyers') }}</dt>
          <dd>{{ formatNumber(live.totals.buyers) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.averageSellOut') }}</dt>
          <dd>{{ duration(live.totals.averageSellOutSeconds) }}</dd>
        </div>
        <div class="stat">
          <dt>{{ t('admin.drops.live.cancelled') }}</dt>
          <dd>{{ formatNumber(live.totals.cancelledOrders) }}</dd>
        </div>
      </dl>

      <div class="admin-table-scroll">
        <table class="admin-table">
          <thead>
            <tr>
              <th scope="col">{{ t('admin.drops.live.product') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.allocated') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.reserved') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.sold') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.available') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.revenue') }}</th>
              <th scope="col" class="num">{{ t('admin.drops.live.soldOutAfter') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in live.products" :key="product.id">
              <td>{{ product.name }}</td>
              <td class="num">{{ product.allocated }}</td>
              <td class="num">{{ product.reserved }}</td>
              <td class="num">{{ product.sold }}</td>
              <td class="num">{{ product.available }}</td>
              <td class="num">{{ formatMoney(product.revenueCents, 'EUR') }}</td>
              <td class="num">{{ duration(product.sellOutSeconds) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="ended && live.leftover === 'KEEP'" class="admin-panel">
      <h2>{{ t('admin.drops.leftover') }}</h2>
      <p v-if="live.leftoverReturnedAt" class="admin-hint">{{ t('admin.drops.live.leftoverReturned', { date: formatDateTime(live.leftoverReturnedAt) }) }}</p>
      <template v-else>
        <p class="admin-hint">{{ t('admin.drops.live.returnLeftoverHint') }}</p>
        <div class="admin-actions">
          <button class="btn btn-primary btn-small" type="button" :disabled="busy" @click="returnLeftover">
            {{ t('admin.drops.live.returnLeftover') }}
          </button>
        </div>
      </template>
    </section>
  </template>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.message {
  margin-bottom: var(--space-3);
}

.status {
  display: inline-block;
  margin-right: var(--space-2);
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-muted-soft);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 700;
}

.status.live {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.status.ended {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: var(--space-3);
  margin: var(--space-3) 0;
}

.stat {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.stat dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 650;
}

.stat dd {
  margin: var(--space-1) 0 0;
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-line);
}

.speed-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-1);
}

.speed-row .admin-input {
  width: 7rem;
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.danger {
  background: var(--color-danger);
  color: var(--color-on-brand);
}

.danger:hover:not(:disabled) {
  filter: brightness(1.1);
}
</style>
