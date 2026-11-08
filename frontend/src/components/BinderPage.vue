<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import BallMark from './BallMark.vue'
import CardImage from './catalog/CardImage.vue'
import { binderApi } from '../services/api'
import { useSession } from '../stores/session'
import { dexLabel } from '../utils/catalog'
import { t } from '../utils/i18n'

const POCKETS = 9

// From this width two pages fit side by side. Narrower, the pockets would get too small to see a card,
// so a phone turns one page at a time.
const SPREAD_MIN_WIDTH = 560

const session = useSession()

const root = ref(null)
const wide = ref(false)
// The first page on show, counted from 0. With two pages open this is the left one, or the first page itself.
const firstPage = ref(0)
// Per page: its slots, kept while turning back and forth.
const pages = ref({})
const meta = ref(null)
const pending = ref(0)
// Sources sometimes link to an image that doesn't exist. Such a card shows as an empty pocket with its name.
const failedCards = ref(new Set())

const loggedIn = computed(() => session.isLoggedIn.value)
const totalPages = computed(() => meta.value?.totalPages ?? 0)

/**
 * The pages on show, like in a real binder: page 1 alone on the right with the inside of the cover on its left,
 * then 2 and 3, then 4 and 5. Null is the inside of the cover.
 */
const shown = computed(() => {
  if (!wide.value) return [firstPage.value]
  if (firstPage.value === 0) return [null, 0]
  const left = firstPage.value % 2 === 1 ? firstPage.value : firstPage.value - 1
  return [left, left + 1]
})

const shownPages = computed(() => shown.value.filter((index) => index !== null))

const canGoBack = computed(() => shownPages.value[0] > 0)
const canGoForward = computed(() => shownPages.value.at(-1) < totalPages.value - 1)

const pageLabel = computed(() => {
  const numbers = shownPages.value.filter((index) => index < totalPages.value).map((index) => index + 1)
  if (numbers.length === 2) return t('binder.pagesOf', { first: numbers[0], second: numbers[1], total: totalPages.value })
  return t('binder.pageOf', { page: numbers[0] ?? 1, total: totalPages.value })
})

/** What the binder shows. The fallback has a text of its own, because it isn't what the customer's setting says. */
const caption = computed(() => {
  if (!meta.value) return ''
  if (meta.value.fallback) return t('binder.fallback')
  if (meta.value.game === 'pokemon') return t('binder.sources.MOST_EXPENSIVE_POKEMON')
  return t(`binder.sources.${meta.value.source}`)
})

const hint = computed(() => {
  if (!meta.value || !loggedIn.value || meta.value.fallback || totalPages.value > 0) return ''
  return t(`binder.empty.${meta.value.source}`)
})

/** Always nine pockets, so a page that isn't full still looks like a sheet of a binder. */
function pocketsOf(index) {
  const slots = pages.value[index] ?? []
  return Array.from({ length: POCKETS }, (_, pocket) => slots[pocket] ?? {})
}

const cardLink = (card) => ({
  name: 'card',
  params: { game: card.game, setCode: card.setCode, cardId: card.id },
})

const pokedexLink = (dexNumber) => ({ name: 'pokedex-entry', params: { dexNumber } })

async function loadPage(index) {
  pending.value++
  try {
    const result = await binderApi.page(index)
    pages.value[index] = result.slots
    meta.value = result
  } catch {
    // The hero stays usable: the pockets of this page just stay empty.
  } finally {
    pending.value--
  }
}

function load() {
  const known = meta.value !== null
  shownPages.value
    .filter((index) => !(index in pages.value) && (!known || index < totalPages.value))
    .forEach(loadPage)
}

function forward() {
  firstPage.value = shownPages.value.at(-1) + 1
}

function back() {
  const first = shownPages.value[0]
  firstPage.value = wide.value ? Math.max(first - 2, 0) : first - 1
}

let observer = null

onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    wide.value = entry.contentRect.width >= SPREAD_MIN_WIDTH
  })
  observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch(shown, load, { immediate: true })
</script>

<template>
  <figure ref="root" class="binder" :class="{ wide }" :aria-label="t('binder.label')">
    <div class="sheets" :aria-busy="pending > 0">
      <template v-for="(index, side) in shown" :key="index ?? 'cover'">
        <div v-if="index === null" class="sheet cover" aria-hidden="true">
          <BallMark class="cover-ball" outline />
        </div>

        <div v-else class="sheet" :class="wide && side === 0 ? 'left' : 'right'">
          <span v-for="hole in 3" :key="hole" class="hole" aria-hidden="true"></span>
          <ul :key="index" class="pockets">
            <li v-for="(pocket, number) in pocketsOf(index)" :key="number" class="slot" :style="{ '--order': number }">
              <router-link
                v-if="pocket.card && !failedCards.has(pocket.card.id)"
                class="pocket filled"
                :to="cardLink(pocket.card)"
                :aria-label="pocket.card.name"
              >
                <CardImage
                  :src="pocket.card.imageUrl"
                  :alt="pocket.card.name"
                  eager
                  @failed="failedCards.add(pocket.card.id)"
                />
              </router-link>
              <router-link v-else-if="pocket.card" class="pocket" :to="cardLink(pocket.card)">
                <span class="pocket-name">{{ pocket.card.name }}</span>
              </router-link>
              <router-link v-else-if="pocket.dexNumber" class="pocket" :to="pokedexLink(pocket.dexNumber)">
                <span>{{ dexLabel(pocket.dexNumber) }}</span>
                <span class="pocket-name">{{ pocket.name }}</span>
              </router-link>
              <span v-else class="pocket"></span>
            </li>
          </ul>
        </div>
      </template>

      <!-- The rings of the binder, through the holes of both pages. -->
      <template v-if="wide">
        <span v-for="ring in 3" :key="ring" class="ring" aria-hidden="true"></span>
      </template>
    </div>

    <figcaption class="foot">
      <span class="caption">
        {{ caption }}
        <router-link v-if="loggedIn" class="change" :to="{ path: '/account', hash: '#binder' }">
          {{ t('binder.change') }}
        </router-link>
      </span>
      <span v-if="totalPages > 1" class="pager">
        <button
          class="turn"
          type="button"
          :disabled="!canGoBack"
          :aria-label="t('binder.previous')"
          @click="back"
        >
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <span class="page-number" aria-live="polite">{{ pageLabel }}</span>
        <button
          class="turn"
          type="button"
          :disabled="!canGoForward"
          :aria-label="t('binder.next')"
          @click="forward"
        >
          <ChevronRight :size="20" aria-hidden="true" />
        </button>
      </span>
      <span v-if="hint" class="hint">{{ hint }}</span>
    </figcaption>
  </figure>
</template>

<style scoped>
.binder {
  margin: 0;
}

.sheets {
  position: relative;
  display: grid;
}

.wide .sheets {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-3);
}

.sheet {
  position: relative;
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-brand-deep);
}

/* In an open binder the holes of both pages sit along the spine in the middle. */
.wide .sheet.left {
  padding: var(--space-4) var(--space-6) var(--space-4) var(--space-4);
}

.wide .sheet.left .hole {
  right: var(--space-2);
  left: auto;
}

.cover {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* The inside of the front cover, with the ball of the shop pressed into it. */
.cover-ball {
  width: 62%;
  color: var(--color-on-brand);
  opacity: 0.16;
}

.hole {
  position: absolute;
  left: var(--space-2);
  width: 0.75rem;
  aspect-ratio: 1;
  border-radius: var(--radius-round);
  background: var(--color-brand);
}

.hole:nth-of-type(1) {
  top: 16%;
}

.hole:nth-of-type(2) {
  top: calc(50% - 0.375rem);
}

.hole:nth-of-type(3) {
  bottom: 16%;
}

.ring {
  position: absolute;
  left: 50%;
  width: 3.25rem;
  height: 0.5rem;
  border-radius: var(--radius-round);
  background: var(--color-on-brand-soft);
  transform: translateX(-50%);
}

.ring:nth-of-type(1) {
  top: calc(16% + 0.125rem);
}

.ring:nth-of-type(2) {
  top: calc(50% - 0.25rem);
}

.ring:nth-of-type(3) {
  bottom: calc(16% + 0.125rem);
}

.pockets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.pocket {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  aspect-ratio: 63 / 88;
  padding: var(--space-1);
  border: 1.5px dashed var(--color-on-brand-soft);
  border-radius: var(--radius-card);
  color: var(--color-on-brand-soft);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 1.2;
  text-align: center;
  text-decoration: none;
}

.pocket-name {
  margin-top: var(--space-half);
  font-size: var(--text-2xs);
  font-weight: 500;
  overflow-wrap: anywhere;
}

/* A filled pocket is only the card: nothing around it, and nothing on top of it. */
.pocket.filled {
  display: block;
  padding: 0;
  border: 0;
  animation: slot-in 420ms cubic-bezier(0.2, 0.8, 0.3, 1) backwards;
  animation-delay: calc(var(--order) * 50ms);
}

@keyframes slot-in {
  from {
    opacity: 0;
    transform: translateY(-1.25rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pocket.filled {
    animation: none;
  }
}

.foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  margin-top: var(--space-2);
  color: var(--color-on-brand-soft);
  font-size: var(--text-sm);
}

.caption {
  flex: 1 1 8rem;
}

.hint {
  flex: 1 1 100%;
}

.change {
  color: var(--color-on-brand);
  white-space: nowrap;
}

.pager {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-left: auto;
}

.page-number {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.turn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
  cursor: pointer;
}

.turn:hover:not(:disabled) {
  background: var(--color-brand-raised);
}

.turn:disabled {
  cursor: default;
  opacity: 0.45;
}
</style>
