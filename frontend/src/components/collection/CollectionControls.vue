<script setup>
import { computed, ref } from 'vue'
import QuantityStepper from './QuantityStepper.vue'
import { collectionApi } from '../../services/api'
import { variantLabel } from '../../utils/catalog'

/**
 * Adds and removes copies of a card. Normally one button works on the first variant the customer owns, and on the
 * card's most common one when they own none. In master mode every variant gets its own row.
 */
const props = defineProps({
  card: { type: Object, required: true },
  master: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

const busy = ref(false)
const error = ref('')

const entries = computed(() => props.card.owned ?? [])
const total = computed(() => entries.value.reduce((sum, entry) => sum + entry.quantity, 0))

const variantRows = computed(() =>
  props.card.variants.map((variant) => ({
    variant,
    entry: entries.value.find((entry) => entry.variant === variant) ?? null,
  })),
)

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    emit('change', await action())
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const add = (variant) => run(() => collectionApi.add({ cardId: props.card.id, variant }))

function decrease(entry) {
  run(() =>
    entry.quantity > 1
      ? collectionApi.update(entry.id, { quantity: entry.quantity - 1, condition: entry.condition })
      : collectionApi.remove(entry.id),
  )
}
</script>

<template>
  <div class="controls" :class="{ master }">
    <ul v-if="master" class="rows">
      <li v-for="row in variantRows" :key="row.variant" class="row">
        <span class="variant">{{ variantLabel(row.variant) }}</span>
        <QuantityStepper
          :quantity="row.entry?.quantity ?? 0"
          :name="`${card.name}, ${variantLabel(row.variant)}`"
          :disabled="busy"
          @increase="add(row.variant)"
          @decrease="decrease(row.entry)"
        />
      </li>
    </ul>

    <QuantityStepper
      v-else
      :quantity="total"
      :name="card.name"
      :disabled="busy"
      @increase="add(entries[0]?.variant ?? null)"
      @decrease="decrease(entries[0])"
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
