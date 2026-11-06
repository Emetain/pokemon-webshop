<script setup>
import { computed, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import QuantityStepper from './QuantityStepper.vue'
import { collectionApi } from '../../services/api'
import { variantLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

/**
 * Adds and removes copies of a card. Normally one row works on the first variant the customer owns, and on the
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
  <div class="controls">
    <ul v-if="master" class="rows">
      <li v-for="row in variantRows" :key="row.variant" class="row">
        <span class="variant">{{ variantLabel(row.variant) }}</span>
        <QuantityStepper
          v-if="row.entry"
          :quantity="row.entry.quantity"
          :name="`${card.name}, ${variantLabel(row.variant)}`"
          :disabled="busy"
          @increase="add(row.variant)"
          @decrease="decrease(row.entry)"
        />
        <button
          v-else
          class="add small"
          type="button"
          :disabled="busy"
          :aria-label="t('collection.addVariant', { variant: `${card.name}, ${variantLabel(row.variant)}` })"
          @click="add(row.variant)"
        >
          <Plus :size="16" aria-hidden="true" />
        </button>
      </li>
    </ul>

    <QuantityStepper
      v-else-if="entries.length"
      :quantity="total"
      :name="card.name"
      :disabled="busy"
      @increase="add(entries[0].variant)"
      @decrease="decrease(entries[0])"
    />

    <button
      v-else
      class="add"
      type="button"
      :disabled="busy"
      :aria-label="t('collection.addCard', { name: card.name })"
      @click="add(null)"
    >
      <Plus :size="16" aria-hidden="true" />
      {{ t('collection.add') }}
    </button>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.controls {
  margin-top: var(--space-2);
}

/* As wide as the add button it replaces, so the tiles in a row keep lining up. */
.controls > .stepper {
  width: 100%;
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

.add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  width: 100%;
  min-height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-brand);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-brand);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 650;
}

.add:hover:not(:disabled) {
  background: var(--color-tint);
}

.add:disabled {
  cursor: progress;
  opacity: 0.5;
}

.add.small {
  width: var(--touch-target);
  padding: 0;
}

.error {
  margin-top: var(--space-1);
  color: var(--color-danger);
  font-size: var(--text-xs);
}
</style>
