<script setup>
import { computed, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'
import { BookHeart, Heart } from 'lucide-vue-next'
import QuantityStepper from './QuantityStepper.vue'
import { binderApi, collectionApi, wishlistApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { variantLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

const CONDITIONS = ['MINT', 'NEAR_MINT', 'EXCELLENT', 'GOOD', 'LIGHT_PLAYED', 'PLAYED', 'POOR']

/** What the customer has of one card, with the wishlist and the home binder. */
const props = defineProps({
  card: { type: Object, required: true },
})

const route = useRoute()
const session = useSession()
const id = useId()

const state = ref(null)
const error = ref('')
// Copies tapped on per variant that the shop hasn't saved yet: the number shows them at once.
const pending = ref({})
// The wishlist, binder and condition controls wait for their own change to be saved.
const working = ref(0)
const busy = computed(() => working.value > 0)

const rows = computed(() =>
  props.card.variants.map((variant) => ({
    variant,
    entry: state.value?.entries.find((entry) => entry.variant === variant) ?? null,
  })),
)

const binderHint = computed(() => state.value?.inBinder && session.state.user?.binderSource !== 'OWN_SELECTION')

async function load() {
  state.value = null
  pending.value = {}
  if (!session.isLoggedIn.value) return
  try {
    state.value = await collectionApi.card(props.card.id)
  } catch (exception) {
    error.value = exception.message
  }
}

/*
 * Every change goes to the shop in turn, so each one works on what the one before it saved. The + and - never wait:
 * a tap counts at once and the next tap can follow straight away.
 */
let queue = Promise.resolve()

function enqueue(action, settled) {
  error.value = ''
  queue = queue.then(async () => {
    try {
      await action()
    } catch (exception) {
      error.value = exception.message
    } finally {
      settled()
    }
  })
}

function run(action) {
  working.value++
  enqueue(action, () => working.value--)
}

const entryOf = (variant) => state.value?.entries.find((entry) => entry.variant === variant)

/** Applies what the shop saved, unless the page has moved on to another card in the meantime. */
function applyChange(cardId, change) {
  if (props.card.id === cardId && state.value) state.value.entries = change.entries
}

function step(variant, delta, action) {
  const cardId = props.card.id
  pending.value[variant] = (pending.value[variant] ?? 0) + delta
  enqueue(
    async () => {
      const change = await action()
      if (change) applyChange(cardId, change)
    },
    () => {
      if (props.card.id === cardId) pending.value[variant] -= delta
    },
  )
}

const add = (variant) => step(variant, 1, () => collectionApi.add({ cardId: props.card.id, variant }))

const decrease = (variant) =>
  step(variant, -1, () => {
    const entry = entryOf(variant)
    if (!entry) return null
    return entry.quantity > 1
      ? collectionApi.update(entry.id, { quantity: entry.quantity - 1, condition: entry.condition })
      : collectionApi.remove(entry.id)
  })

const setCondition = (variant, condition) => {
  const cardId = props.card.id
  run(async () => {
    const entry = entryOf(variant)
    if (!entry) return
    applyChange(cardId, await collectionApi.update(entry.id, { quantity: entry.quantity, condition: condition || null }))
  })
}

const toggleWishlist = () =>
  run(async () => {
    const on = state.value.onWishlist
    await (on ? wishlistApi.remove(props.card.id) : wishlistApi.add(props.card.id))
    state.value.onWishlist = !on
  })

const toggleBinder = () =>
  run(async () => {
    const on = state.value.inBinder
    await (on ? binderApi.deselect(props.card.id) : binderApi.select(props.card.id))
    state.value.inBinder = !on
  })

watch(() => props.card.id, load, { immediate: true })
</script>

<template>
  <section class="panel">
    <h2>{{ t('collection.card.title') }}</h2>

    <p v-if="!session.isLoggedIn.value" class="guest">
      {{ t('collection.card.guest') }}
      <router-link :to="{ path: '/inloggen', query: { redirect: route.fullPath } }">
        {{ t('collection.card.guestAction') }}
      </router-link>
    </p>

    <template v-else-if="state">
      <ul class="rows">
        <li v-for="row in rows" :key="row.variant" class="row">
          <span class="variant">{{ variantLabel(row.variant) }}</span>
          <QuantityStepper
            :quantity="(row.entry?.quantity ?? 0) + (pending[row.variant] ?? 0)"
            :name="`${card.name}, ${variantLabel(row.variant)}`"
            @increase="add(row.variant)"
            @decrease="decrease(row.variant)"
          />
          <div v-if="row.entry" class="condition">
            <label class="condition-label" :for="`${id}-${row.variant}`">{{ t('collection.card.condition') }}</label>
            <select
              :id="`${id}-${row.variant}`"
              class="condition-input"
              :value="row.entry.condition ?? ''"
              :disabled="busy"
              @change="setCondition(row.variant, $event.target.value)"
            >
              <option value="">{{ t('collection.card.noCondition') }}</option>
              <option v-for="condition in CONDITIONS" :key="condition" :value="condition">
                {{ t(`collection.conditions.${condition}`) }}
              </option>
            </select>
          </div>
        </li>
      </ul>

      <div class="actions">
        <button class="toggle" type="button" :aria-pressed="state.onWishlist" :disabled="busy" @click="toggleWishlist">
          <Heart :size="18" :fill="state.onWishlist ? 'currentColor' : 'none'" aria-hidden="true" />
          {{ state.onWishlist ? t('collection.card.wishlistRemove') : t('collection.card.wishlistAdd') }}
        </button>
        <button class="toggle" type="button" :aria-pressed="state.inBinder" :disabled="busy" @click="toggleBinder">
          <BookHeart :size="18" aria-hidden="true" />
          {{ state.inBinder ? t('collection.card.binderRemove') : t('collection.card.binderAdd') }}
        </button>
      </div>
      <p v-if="binderHint" class="hint">
        <router-link :to="{ path: '/account', hash: '#binder' }">{{ t('collection.card.binderHint') }}</router-link>
      </p>
    </template>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.panel {
  margin-top: var(--space-5);
}

.guest {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
}

.rows {
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
}

.variant {
  flex: 1 1 7rem;
  font-size: var(--text-sm);
  font-weight: 600;
}

.condition {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.condition-label {
  font-size: var(--text-xs);
  color: var(--color-ink-soft);
}

.condition-input {
  height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-4);
  margin-top: var(--space-2);
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

@media (hover: hover) {
  .toggle:hover:not(:disabled) {
    text-decoration: underline;
  }
}

.hint {
  font-size: var(--text-xs);
}

.error {
  margin-top: var(--space-2);
  color: var(--color-danger);
  font-size: var(--text-sm);
}
</style>
