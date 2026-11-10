<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Clock, DoorOpen, Hourglass, PartyPopper, ShoppingBag, TimerOff, Users } from 'lucide-vue-next'
import DropCountdown from './DropCountdown.vue'
import DropReminderForm from './DropReminderForm.vue'
import FormMessage from '../FormMessage.vue'
import { formatDateTime, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The waiting room part of a drop page: what to do now, and where you are. The page asks the shop for the state,
 * this panel only shows it.
 */
const props = defineProps({
  drop: { type: Object, required: true },
  queue: { type: Object, default: null },
  cart: { type: Object, default: null },
  joining: { type: Boolean, default: false },
  error: { type: String, default: '' },
  waitingRoomMinutes: { type: Number, default: 30 },
  reminderMinutes: { type: Number, default: 15 },
})

const emit = defineEmits(['join', 'expired', 'started'])

const route = useRoute()
const id = useId()
const website = ref('')

const state = computed(() => props.queue?.state ?? 'LOADING')
const live = computed(() => props.drop.status === 'LIVE')
const loginLink = computed(() => ({ path: '/inloggen', query: { redirect: route.fullPath } }))
const registerLink = computed(() => ({ path: '/registreren', query: { redirect: route.fullPath } }))
const time = (instant) => new Intl.DateTimeFormat('nl-NL', { timeStyle: 'short', timeZone: 'Europe/Amsterdam' }).format(new Date(instant))

const waiting = computed(() => {
  const count = props.queue?.waitingCount ?? 0
  return count <= 1 ? t('drops.queue.oneWaiting') : t('drops.queue.waitingCount', { count })
})

const ahead = computed(() => {
  const count = props.queue?.ahead ?? 0
  if (count === 0) return t('drops.queue.noneAhead')
  return count === 1 ? t('drops.queue.oneAhead') : t('drops.queue.ahead', { count })
})

const estimate = computed(() => {
  const seconds = props.queue?.estimatedSeconds
  if (seconds == null || seconds <= 20) return t('drops.queue.estimateSoon')
  const minutes = Math.round(seconds / 60)
  const text = minutes < 1 ? t('drops.queue.lessThanMinute') : minutes === 1 ? t('drops.queue.oneMinute') : t('drops.queue.minutes', { count: minutes })
  return t('drops.queue.estimate', { time: text })
})

const cartCount = computed(() => props.cart?.cart.itemCount ?? props.queue?.cartCount ?? 0)
const cartText = computed(() =>
  cartCount.value === 1 ? t('drops.queue.oneInCart') : t('drops.queue.cart', { count: cartCount.value }),
)

/** The time left to shop, against the server's clock. */
const offset = ref(0)
const now = ref(Date.now())
let timer = null
let expiredSent = false

watch(
  () => props.queue?.serverTime,
  (serverTime) => {
    if (serverTime) offset.value = Date.parse(serverTime) - Date.now()
  },
  { immediate: true },
)

const accessLeft = computed(() => {
  const until = props.queue?.accessExpiresAt
  if (!until) return 0
  return Math.max(0, Date.parse(until) - (now.value + offset.value))
})

const accessText = computed(() => {
  const total = Math.ceil(accessLeft.value / 1000)
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
})

watch(
  () => props.queue?.accessExpiresAt,
  () => (expiredSent = false),
)

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
    if (state.value === 'ADMITTED' && accessLeft.value === 0 && !expiredSent) {
      expiredSent = true
      emit('expired')
    }
  }, 1000)
})

onBeforeUnmount(() => clearInterval(timer))

function join() {
  emit('join', website.value)
}
</script>

<template>
  <section class="panel" :class="`state-${state.toLowerCase()}`" aria-live="polite">
    <p v-if="state === 'LOADING'" class="muted" aria-busy="true">{{ t('drops.queue.loading') }}</p>

    <!-- Not logged in for a drop that needs an account. -->
    <template v-else-if="state === 'LOGIN_REQUIRED'">
      <h2 class="title"><DoorOpen :size="22" aria-hidden="true" />{{ t('drops.queue.loginTitle') }}</h2>
      <p class="text">{{ t('drops.queue.loginText') }}</p>
      <div class="buttons">
        <router-link class="btn btn-primary" :to="loginLink">{{ t('drops.queue.login') }}</router-link>
        <router-link class="btn btn-secondary" :to="registerLink">{{ t('drops.queue.register') }}</router-link>
      </div>
      <DropReminderForm v-if="drop.status === 'SCHEDULED'" class="reminder" :slug="drop.slug" :minutes="reminderMinutes" />
    </template>

    <template v-else-if="state === 'NOT_OPEN'">
      <h2 class="title"><Clock :size="22" aria-hidden="true" />{{ t('drops.queue.notOpenTitle', { time: time(drop.waitingRoomOpensAt) }) }}</h2>
      <p class="text">{{ t('drops.queue.notOpenText', { minutes: waitingRoomMinutes }) }}</p>
      <DropReminderForm class="reminder" :slug="drop.slug" :minutes="reminderMinutes" />
    </template>

    <template v-else-if="state === 'NOT_JOINED'">
      <h2 class="title"><DoorOpen :size="22" aria-hidden="true" />{{ live ? t('drops.queue.joinLiveTitle') : t('drops.queue.joinTitle') }}</h2>
      <p class="text">{{ live ? t('drops.queue.joinLiveText') : t('drops.queue.joinText') }}</p>
      <p v-if="!live && queue.waitingCount" class="count"><Users :size="16" aria-hidden="true" />{{ waiting }}</p>
      <form class="join" @submit.prevent="join">
        <div class="trap" aria-hidden="true">
          <label :for="`${id}-website`">{{ t('drops.checkout.honeypot') }}</label>
          <input :id="`${id}-website`" v-model="website" type="text" name="website" tabindex="-1" autocomplete="off" />
        </div>
        <button class="btn btn-primary btn-big" type="submit" :disabled="joining">
          {{ joining ? t('drops.queue.joining') : live ? t('drops.queue.joinLive') : t('drops.queue.join') }}
        </button>
      </form>
    </template>

    <template v-else-if="state === 'WAITING_ROOM'">
      <h2 class="title"><Hourglass :size="22" aria-hidden="true" />{{ t('drops.queue.waitingTitle') }}</h2>
      <p class="text">{{ t('drops.queue.waitingText') }}</p>
      <div class="waiting-clock">
        <span class="clock-label">{{ t('drops.countdown.label') }}</span>
        <DropCountdown :target="drop.startsAt" :server-time="queue.serverTime" @done="emit('started')" />
      </div>
      <p class="count"><Users :size="16" aria-hidden="true" />{{ waiting }}</p>
    </template>

    <template v-else-if="state === 'QUEUED'">
      <h2 class="title"><Users :size="22" aria-hidden="true" />{{ t('drops.queue.queuedTitle') }}</h2>
      <div class="position">
        <span class="position-label">{{ t('drops.queue.position') }}</span>
        <span class="position-number">{{ queue.position }}</span>
      </div>
      <p class="ahead">{{ ahead }}</p>
      <p class="estimate">{{ estimate }}</p>
      <FormMessage v-if="queue.paused" type="success">{{ t('drops.queue.paused') }}</FormMessage>
      <p class="hint">{{ t('drops.queue.keepOpen') }}</p>
    </template>

    <template v-else-if="state === 'ADMITTED'">
      <h2 class="title"><PartyPopper :size="22" aria-hidden="true" />{{ t('drops.queue.admittedTitle') }}</h2>
      <p class="text">{{ t('drops.queue.admittedText') }}</p>
      <p class="timer" :class="{ urgent: accessLeft < 120000 }" role="timer">
        <span class="visually-hidden">{{ t('drops.queue.timeLeftLabel') }}</span>
        {{ t('drops.queue.timeLeft', { time: accessText }) }}
      </p>
      <div class="cart-row">
        <span class="cart-summary">
          <ShoppingBag :size="18" aria-hidden="true" />
          <template v-if="cartCount">
            {{ cartText }}
            <strong v-if="cart">· {{ formatMoney(cart.cart.subtotalCents, 'EUR') }}</strong>
          </template>
          <template v-else>{{ t('drops.queue.emptyCart') }}</template>
        </span>
        <router-link
          class="btn btn-primary"
          :class="{ disabled: !cartCount }"
          :to="`/drops/${drop.slug}/afrekenen`"
          :aria-disabled="!cartCount"
          :tabindex="cartCount ? undefined : -1"
        >
          {{ t('drops.queue.checkout') }}
        </router-link>
      </div>
    </template>

    <template v-else-if="state === 'EXPIRED'">
      <h2 class="title"><TimerOff :size="22" aria-hidden="true" />{{ t('drops.queue.expiredTitle') }}</h2>
      <p class="text">{{ t('drops.queue.expiredText') }}</p>
      <button class="btn btn-primary" type="button" :disabled="joining" @click="emit('join', '')">{{ t('drops.queue.rejoin') }}</button>
    </template>

    <template v-else-if="state === 'CLOSED'">
      <h2 class="title">{{ drop.soldOut ? t('drops.page.soldOutTitle') : t('drops.page.endedTitle') }}</h2>
      <p class="text">{{ drop.soldOut ? t('drops.page.soldOutText') : t('drops.page.endedText') }}</p>
      <router-link class="btn btn-secondary" to="/drops">{{ t('drops.page.toDrops') }}</router-link>
    </template>

    <FormMessage v-if="error" class="error">{{ error }}</FormMessage>
    <p v-if="drop.endsAt && live" class="ends">{{ t('drops.page.endsAt', { date: formatDateTime(drop.endsAt) }) }}</p>
  </section>
</template>

<style scoped>
.panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 2px solid var(--color-brand);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.state-admitted {
  border-color: var(--color-success);
}

.state-closed,
.state-loading {
  border-color: var(--color-line);
  box-shadow: none;
}

.title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
}

.state-admitted .title {
  color: var(--color-success);
}

.text,
.muted,
.hint,
.ends {
  color: var(--color-ink-soft);
}

.hint,
.ends {
  font-size: var(--text-sm);
}

.buttons,
.join {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.btn-big {
  min-width: 16rem;
  font-size: var(--text-lg);
}

.reminder {
  margin-top: var(--space-2);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-line);
}

.count {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 650;
}

.waiting-clock {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--text-2xl);
}

.clock-label {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.position {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.position-label {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.position-number {
  color: var(--color-brand);
  font-family: var(--font-display);
  font-size: clamp(3rem, 12vw, 4.5rem);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.ahead {
  font-size: var(--text-lg);
  font-weight: 700;
}

.estimate {
  color: var(--color-ink-soft);
}

.timer {
  justify-self: start;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--text-lg);
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.timer.urgent {
  background: var(--color-sale-soft);
  color: var(--color-sale);
}

.cart-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-line);
}

.cart-summary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.btn.disabled {
  opacity: 0.5;
  pointer-events: none;
}

.trap {
  position: absolute;
  left: -10000px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}
</style>
