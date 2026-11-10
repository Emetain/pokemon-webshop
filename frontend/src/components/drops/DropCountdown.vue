<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { t } from '../../utils/i18n'

/**
 * Counts down to the start of a drop. The browser's clock can be minutes off, so the time left is measured against
 * the server's clock: `serverTime` is when the server answered. `boxes` shows big blocks for the drop page, without
 * it the countdown is a short line for the hero. Emits `done` once at zero.
 */
const props = defineProps({
  target: { type: String, required: true },
  serverTime: { type: String, default: null },
  boxes: { type: Boolean, default: false },
})

const emit = defineEmits(['done'])

const offset = ref(0)
const now = ref(Date.now())
let timer = null
let doneSent = false

watch(
  () => props.serverTime,
  (serverTime) => {
    if (serverTime) offset.value = Date.parse(serverTime) - Date.now()
  },
  { immediate: true },
)

watch(
  () => props.target,
  () => (doneSent = false),
)

const remaining = computed(() => Math.max(0, Date.parse(props.target) - (now.value + offset.value)))

const parts = computed(() => {
  const total = Math.floor(remaining.value / 1000)
  return {
    days: Math.floor(total / 86400),
    hours: Math.floor((total % 86400) / 3600),
    minutes: Math.floor((total % 3600) / 60),
    seconds: total % 60,
  }
})

const pad = (value) => String(value).padStart(2, '0')

const line = computed(() => {
  const { days, hours, minutes, seconds } = parts.value
  const clock = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  if (days > 0) return `${days} ${days === 1 ? t('drops.countdown.day') : t('drops.countdown.days')} ${clock}`
  return clock
})

/** For screen readers, once a minute is plenty: the visible clock is hidden from them. */
const spoken = computed(() => {
  const { days, hours, minutes } = parts.value
  const pieces = []
  if (days) pieces.push(`${days} ${days === 1 ? t('drops.countdown.day') : t('drops.countdown.days')}`)
  if (hours) pieces.push(`${hours} ${t('drops.countdown.hours')}`)
  pieces.push(`${minutes} ${t('drops.countdown.minutes')}`)
  return t('drops.countdown.spoken', { text: pieces.join(' ') })
})

const blocks = computed(() => {
  const { days, hours, minutes, seconds } = parts.value
  return [
    ...(days > 0 ? [{ key: 'days', value: String(days), label: days === 1 ? t('drops.countdown.day') : t('drops.countdown.days') }] : []),
    { key: 'hours', value: pad(hours), label: t('drops.countdown.hours') },
    { key: 'minutes', value: pad(minutes), label: t('drops.countdown.minutes') },
    { key: 'seconds', value: pad(seconds), label: t('drops.countdown.seconds') },
  ]
})

function tick() {
  now.value = Date.now()
  if (remaining.value === 0 && !doneSent) {
    doneSent = true
    emit('done')
  }
}

onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="countdown" :class="{ boxes }" role="timer">
    <span class="visually-hidden">{{ remaining > 0 ? spoken : t('drops.countdown.starting') }}</span>
    <template v-if="boxes">
      <div v-for="block in blocks" :key="block.key" class="block" aria-hidden="true">
        <span class="value">{{ block.value }}</span>
        <span class="label">{{ block.label }}</span>
      </div>
    </template>
    <span v-else class="line" aria-hidden="true">{{ remaining > 0 ? line : t('drops.countdown.starting') }}</span>
  </div>
</template>

<style scoped>
.countdown {
  font-variant-numeric: tabular-nums;
}

.line {
  font-weight: 750;
}

.boxes {
  display: flex;
  gap: var(--space-2);
}

.block {
  display: grid;
  justify-items: center;
  min-width: 4rem;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: rgb(255 255 255 / 14%);
  box-shadow: inset 0 -3px 0 rgb(0 0 0 / 14%);
}

.value {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-stretch: 85%;
  font-weight: 800;
  line-height: 1;
}

.label {
  margin-top: var(--space-1);
  font-size: var(--text-2xs);
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

@media (min-width: 768px) {
  .block {
    min-width: 5rem;
  }

  .value {
    font-size: 2.5rem;
  }
}
</style>
