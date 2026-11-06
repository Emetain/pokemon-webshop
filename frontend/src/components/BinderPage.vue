<script setup>
import { computed, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import CardImage from './catalog/CardImage.vue'
import { binderApi } from '../services/api'
import { useSession } from '../stores/session'
import { dexLabel } from '../utils/catalog'
import { t } from '../utils/i18n'

const POCKETS = 9

const session = useSession()

const binder = ref(null)
const page = ref(0)
const loading = ref(true)
// Sources sometimes link to an image that doesn't exist. Such a card shows as an empty pocket with its name.
const failedCards = ref(new Set())

/** Always nine pockets, so a last page that isn't full still looks like a sheet of a binder. */
const pockets = computed(() => {
  const slots = binder.value?.slots ?? []
  return Array.from({ length: POCKETS }, (_, index) => slots[index] ?? {})
})

const totalPages = computed(() => binder.value?.totalPages ?? 0)

const loggedIn = computed(() => session.isLoggedIn.value)

/** What the binder shows. The fallback has a text of its own, because it isn't what the customer's setting says. */
const caption = computed(() => {
  if (!binder.value) return ''
  return binder.value.fallback ? t('binder.fallback') : t(`binder.sources.${binder.value.source}`)
})

const hint = computed(() => {
  if (!binder.value || !loggedIn.value || binder.value.fallback || binder.value.slots.length) return ''
  return t(`binder.empty.${binder.value.source}`)
})

const cardLink = (card) => ({
  name: 'card',
  params: { game: card.game, setCode: card.setCode, cardId: card.id },
})

const pokedexLink = (dexNumber) => ({ name: 'pokedex-entry', params: { dexNumber } })

let latestRequest = 0

async function load() {
  const request = ++latestRequest
  loading.value = true
  try {
    const result = await binderApi.page(page.value)
    if (request === latestRequest) binder.value = result
  } catch {
    // The hero stays usable: the pockets just stay empty.
    if (request === latestRequest) binder.value = null
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

watch(page, load, { immediate: true })
</script>

<template>
  <figure class="binder" :aria-label="t('binder.label')">
    <div class="sheet" :aria-busy="loading">
      <span v-for="hole in 3" :key="hole" class="hole" aria-hidden="true"></span>
      <ul :key="page" class="pockets">
        <li v-for="(pocket, index) in pockets" :key="index" class="slot" :style="{ '--order': index }">
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
          :disabled="page === 0 || loading"
          :aria-label="t('binder.previous')"
          @click="page--"
        >
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <span class="page-number" aria-live="polite">
          {{ t('binder.pageOf', { page: page + 1, total: totalPages }) }}
        </span>
        <button
          class="turn"
          type="button"
          :disabled="page >= totalPages - 1 || loading"
          :aria-label="t('binder.next')"
          @click="page++"
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

.sheet {
  position: relative;
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-brand-deep);
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
