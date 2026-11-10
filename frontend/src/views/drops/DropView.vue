<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, DoorOpen, Shuffle, Timer, Zap } from 'lucide-vue-next'
import BallMark from '../../components/BallMark.vue'
import DropCountdown from '../../components/drops/DropCountdown.vue'
import DropProductCard from '../../components/drops/DropProductCard.vue'
import DropQueuePanel from '../../components/drops/DropQueuePanel.vue'
import FormMessage from '../../components/FormMessage.vue'
import { findGame } from '../../data/games'
import { dropApi } from '../../services/api'
import { useLiveDrops } from '../../stores/drops'
import { useRegion } from '../../stores/region'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The page of one drop: banner, countdown, products, and the waiting room. It asks the shop for two things: the drop
 * itself with its stock counters, and where this visitor is in line. Both ask again by themselves, faster while the
 * drop is live and when the shop says the visitor's turn is close.
 */
const props = defineProps({
  slug: { type: String, required: true },
})

const region = useRegion()
const liveDrops = useLiveDrops()

const drop = ref(null)
const queue = ref(null)
const cart = ref(null)
const notFound = ref(false)
const loadError = ref('')
const queueError = ref('')
const joining = ref(false)
const changing = ref(false)

const accent = computed(() => (drop.value?.game ? findGame(drop.value.game)?.accent : undefined))
const live = computed(() => drop.value?.status === 'LIVE')
const shopping = computed(() => live.value && queue.value?.state === 'ADMITTED')
const waitingRoomMinutes = computed(() =>
  drop.value ? Math.round((Date.parse(drop.value.startsAt) - Date.parse(drop.value.waitingRoomOpensAt)) / 60000) : 30,
)
const limitOf = (product) => cart.value?.limits.find((limit) => limit.dropProductId === product.id)

let detailTimer = null
let queueTimer = null
let stopped = false

async function loadDrop() {
  try {
    drop.value = await dropApi.get(props.slug)
    loadError.value = ''
  } catch (exception) {
    if (exception.status === 404) notFound.value = true
    else loadError.value = exception.message
  }
  scheduleDrop()
}

/** Stock counters every 5 seconds while live, the start every 30 seconds before it. Nothing after the end. */
function scheduleDrop() {
  clearTimeout(detailTimer)
  if (stopped || notFound.value || drop.value?.status === 'ENDED') return
  detailTimer = setTimeout(loadDrop, live.value ? 5000 : 30000)
}

async function loadQueue() {
  try {
    applyQueue(await dropApi.ping(props.slug))
  } catch (exception) {
    queueError.value = exception.message
    scheduleQueue(15000)
  }
}

function applyQueue(status) {
  const wasAdmitted = queue.value?.state === 'ADMITTED'
  queue.value = status
  queueError.value = ''
  if (status.state === 'ADMITTED' && (!wasAdmitted || !cart.value || cart.value.cart.itemCount !== status.cartCount)) loadCart()
  if (status.state !== 'ADMITTED') cart.value = null
  scheduleQueue(status.pollAfterMs)
}

function scheduleQueue(after) {
  clearTimeout(queueTimer)
  if (stopped || !after) return
  queueTimer = setTimeout(loadQueue, after)
}

async function loadCart() {
  try {
    cart.value = await dropApi.cart(props.slug, region.country.value)
  } catch (exception) {
    if (exception.code?.startsWith('drop_')) loadQueue()
    else queueError.value = exception.message
  }
}

async function join(website) {
  joining.value = true
  queueError.value = ''
  try {
    applyQueue(await dropApi.join(props.slug, { website }))
    // Through right away when the line is empty: ask once more to get the access.
    if (live.value) setTimeout(loadQueue, 1200)
  } catch (exception) {
    queueError.value = exception.message
  } finally {
    joining.value = false
  }
}

async function change(product, quantity) {
  changing.value = true
  queueError.value = ''
  try {
    cart.value = await dropApi.setQuantity(props.slug, product.id, quantity, region.country.value)
    if (queue.value) queue.value = { ...queue.value, cartCount: cart.value.cart.itemCount }
  } catch (exception) {
    queueError.value = exception.message
    if (exception.code?.startsWith('drop_')) loadQueue()
    loadDrop()
  } finally {
    changing.value = false
  }
}

/** At zero the shop starts the drop within a second. Ask both a moment later, with a little spread. */
function onStarted() {
  const delay = 800 + Math.random() * 1500
  setTimeout(() => {
    loadDrop()
    loadQueue()
    liveDrops.load()
  }, delay)
}

watch(
  () => props.slug,
  () => {
    drop.value = null
    queue.value = null
    cart.value = null
    notFound.value = false
    loadDrop()
    loadQueue()
  },
)

onMounted(() => {
  loadDrop()
  loadQueue()
})

onBeforeUnmount(() => {
  stopped = true
  clearTimeout(detailTimer)
  clearTimeout(queueTimer)
})
</script>

<template>
  <div v-if="notFound" class="container page">
    <h1>{{ t('drops.page.notFound') }}</h1>
    <router-link class="btn btn-secondary" to="/drops">{{ t('drops.page.toDrops') }}</router-link>
  </div>

  <div v-else-if="!drop" class="container page">
    <FormMessage v-if="loadError">{{ loadError }}</FormMessage>
    <p v-else class="muted" aria-busy="true">{{ t('drops.loading') }}</p>
  </div>

  <div v-else>
    <section class="hero" :data-game="accent">
      <BallMark v-if="!accent || accent === 'pokemon'" class="hero-ball" outline />
      <div class="container hero-grid" :class="{ 'with-banner': drop.bannerUrl }">
        <div class="hero-text">
          <router-link class="back" to="/drops">
            <ChevronLeft :size="18" aria-hidden="true" />
            {{ t('drops.page.back') }}
          </router-link>
          <p class="status" :class="`status-${drop.status.toLowerCase()}`">
            <span v-if="live" class="dot" aria-hidden="true"></span>
            <Zap v-else :size="14" aria-hidden="true" />
            {{ t(`drops.status.${drop.status}`) }}
          </p>
          <h1 class="title">{{ drop.name }}</h1>
          <p class="when">
            <template v-if="drop.status === 'ENDED'">{{ t('drops.endedOn', { date: formatDateTime(drop.endedAt ?? drop.startsAt) }) }}</template>
            <template v-else-if="live">{{ t('drops.liveSince', { time: formatDateTime(drop.startsAt) }) }}</template>
            <template v-else>{{ t('drops.page.startsAt', { date: formatDateTime(drop.startsAt) }) }}</template>
          </p>
          <DropCountdown
            v-if="drop.status === 'SCHEDULED' || drop.status === 'DRAFT'"
            class="hero-countdown"
            boxes
            :target="drop.startsAt"
            :server-time="drop.serverTime"
            @done="onStarted"
          />
          <p v-if="drop.requiresAccount && drop.status !== 'ENDED'" class="note">{{ t('drops.page.accountRequired') }}</p>
          <p v-if="drop.maxPerCustomer" class="note">{{ t('drops.page.maxPerCustomer', { count: drop.maxPerCustomer }) }}</p>
        </div>
        <img v-if="drop.bannerUrl" class="banner" :src="drop.bannerUrl" alt="" decoding="async" />
      </div>
    </section>

    <div class="container body">
      <FormMessage v-if="drop.status === 'DRAFT'" type="success">{{ t('drops.page.preview') }}</FormMessage>

      <div class="layout">
        <div class="main">
          <DropQueuePanel
            v-if="drop.status !== 'DRAFT'"
            :drop="drop"
            :queue="queue"
            :cart="cart"
            :joining="joining"
            :error="queueError"
            :waiting-room-minutes="waitingRoomMinutes"
            :reminder-minutes="drop.reminderMinutes"
            @join="join"
            @expired="loadQueue"
            @started="onStarted"
          />

          <section class="products" aria-labelledby="drop-products">
            <h2 id="drop-products">{{ t('drops.page.productsTitle') }}</h2>
            <ul class="product-list">
              <li v-for="product in drop.products" :key="product.id">
                <DropProductCard
                  :product="product"
                  :live="live || drop.status === 'ENDED'"
                  :shopping="shopping"
                  :in-cart="limitOf(product)?.inCart ?? 0"
                  :room="limitOf(product)?.room ?? null"
                  :ordered="limitOf(product)?.ordered ?? 0"
                  :busy="changing"
                  @change="change(product, $event)"
                />
              </li>
            </ul>
          </section>

          <section v-if="drop.description" class="about" aria-labelledby="drop-about">
            <h2 id="drop-about">{{ t('drops.page.about') }}</h2>
            <p class="description">{{ drop.description }}</p>
          </section>
        </div>

        <aside class="how" aria-labelledby="drop-how">
          <h2 id="drop-how">{{ t('drops.page.howTitle') }}</h2>
          <ol class="steps">
            <li>
              <DoorOpen class="step-icon" :size="22" aria-hidden="true" />
              <div>
                <h3>{{ t('drops.page.how1Title') }}</h3>
                <p>{{ t('drops.page.how1', { minutes: waitingRoomMinutes }) }}</p>
              </div>
            </li>
            <li>
              <Shuffle class="step-icon" :size="22" aria-hidden="true" />
              <div>
                <h3>{{ t('drops.page.how2Title') }}</h3>
                <p>{{ t('drops.page.how2') }}</p>
              </div>
            </li>
            <li>
              <Timer class="step-icon" :size="22" aria-hidden="true" />
              <div>
                <h3>{{ t('drops.page.how3Title') }}</h3>
                <p>{{ t('drops.page.how3', { minutes: drop.accessMinutes }) }}</p>
              </div>
            </li>
          </ol>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding-block: var(--space-5) 0;
}

.muted {
  color: var(--color-ink-soft);
}

.hero {
  position: relative;
  overflow: hidden;
  background: var(--accent);
  color: var(--accent-ink);
}

.hero :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.hero-ball {
  position: absolute;
  top: -8rem;
  right: -14rem;
  width: 28rem;
  height: 28rem;
  color: var(--accent-ink);
  opacity: 0.14;
  transform: rotate(-18deg);
}

.hero-grid {
  position: relative;
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-4) var(--space-6);
}

.hero-text {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  color: var(--accent-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.back:hover {
  text-decoration: underline;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-half) var(--space-3);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.status-live {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-round);
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.3;
  }
}

.title {
  font-size: var(--text-hero);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 0.98;
}

.when {
  font-size: var(--text-lg);
  font-weight: 650;
}

.hero-countdown {
  margin-top: var(--space-1);
}

.note {
  font-size: var(--text-sm);
  opacity: 0.9;
}

.banner {
  width: 100%;
  max-height: 22rem;
  border-radius: var(--radius-lg);
  object-fit: cover;
  box-shadow: 0 18px 36px rgb(0 0 0 / 22%);
}

.body {
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-5) 0;
}

.layout {
  display: grid;
  gap: var(--space-6);
}

.main {
  display: grid;
  gap: var(--space-6);
  min-width: 0;
}

.products h2,
.about h2,
.how h2 {
  font-size: var(--text-xl);
}

.product-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.description {
  margin-top: var(--space-3);
  white-space: pre-line;
}

.how {
  align-self: start;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-tint);
}

.steps {
  display: grid;
  gap: var(--space-4);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.steps li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
}

.step-icon {
  color: var(--color-brand);
}

.steps h3 {
  font-size: var(--text-md);
}

.steps p {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

@media (min-width: 768px) {
  .product-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-grid.with-banner {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: center;
  }
}

@media (min-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr) 20rem;
  }

  .how {
    position: sticky;
    top: 9rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dot {
    animation: none;
  }
}
</style>
