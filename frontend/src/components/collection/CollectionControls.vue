<script setup>
import { computed, ref } from 'vue'
import QuantityStepper from './QuantityStepper.vue'
import { collectionApi } from '../../services/api'
import { variantLabel } from '../../utils/catalog'

/**
 * Adds and removes copies of a card. Normally one button works on the first variant the customer owns, and on the
 * card's most common one when they own none. In master mode every variant gets its own row.
 *
 * A tap counts at once: the number changes before the shop has saved it, and the button can be tapped again straight
 * away. The taps go to the shop one after the other, and a tap that fails to save is taken back off the number.
 */
const props = defineProps({
  card: { type: Object, required: true },
  master: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

const error = ref('')
// Copies tapped on but not saved yet, per variant. Without master mode they all count under ANY.
const pending = ref({})
const ANY = '*'

const entries = computed(() => props.card.owned ?? [])
const total = computed(() => entries.value.reduce((sum, entry) => sum + entry.quantity, 0))
const pendingTotal = computed(() => Object.values(pending.value).reduce((sum, delta) => sum + delta, 0))

const variantRows = computed(() =>
  props.card.variants.map((variant) => ({
    variant,
    entry: entries.value.find((entry) => entry.variant === variant) ?? null,
  })),
)

let queue = Promise.resolve()

/** `action` runs when it is its turn, so it works on what the shop has saved by then. */
function enqueue(key, delta, action) {
  error.value = ''
  pending.value[key] = (pending.value[key] ?? 0) + delta
  queue = queue.then(async () => {
    try {
      const change = await action()
      if (change) emit('change', change)
    } catch (exception) {
      error.value = exception.message
    } finally {
      pending.value[key] -= delta
    }
  })
}

const entryFor = (variant) =>
  variant === ANY ? entries.value[0] : entries.value.find((entry) => entry.variant === variant)

function add(variant) {
  enqueue(variant, 1, () =>
    collectionApi.add({ cardId: props.card.id, variant: variant === ANY ? (entries.value[0]?.variant ?? null) : variant }),
  )
}

function decrease(variant) {
  enqueue(variant, -1, () => {
    const entry = entryFor(variant)
    if (!entry) return null
    return entry.quantity > 1
      ? collectionApi.update(entry.id, { quantity: entry.quantity - 1, condition: entry.condition })
      : collectionApi.remove(entry.id)
  })
}
</script>

<template>
  <div class="controls" :class="{ master }">
    <ul v-if="master" class="rows">
      <li v-for="row in variantRows" :key="row.variant" class="row">
        <span class="variant">{{ variantLabel(row.variant) }}</span>
        <QuantityStepper
          :quantity="(row.entry?.quantity ?? 0) + (pending[row.variant] ?? 0)"
          :name="`${card.name}, ${variantLabel(row.variant)}`"
          @increase="add(row.variant)"
          @decrease="decrease(row.variant)"
        />
      </li>
    </ul>

    <QuantityStepper
      v-else
      :quantity="total + pendingTotal"
      :name="card.name"
      @increase="add(ANY)"
      @decrease="decrease(ANY)"
    />

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.controls.master {
  align-items: stretch;
  margin-top: var(--space-2);
}

.rows {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-2);
}

.variant {
  min-width: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.error {
  margin-top: var(--space-1);
  color: var(--color-danger);
  font-size: var(--text-xs);
  text-align: right;
}
</style>
