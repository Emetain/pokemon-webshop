<script setup>
import { reactive, ref, useId } from 'vue'
import { PackagePlus, Trash2, X } from 'lucide-vue-next'
import CardPicker from './CardPicker.vue'
import FormMessage from '../FormMessage.vue'
import { eurosToCents } from '../../data/shop'
import { adminShopApi } from '../../services/api'
import { formatDateTime, formatMoney, formatPercent } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The batches of a mystery box. The owner packs a batch by hand and enters what is in it here: every box goes on the
 * stock, the shop page shows the chances, and a paid order gets a random box from what is left.
 */
const props = defineProps({
  productId: { type: Number, required: true },
  game: { type: String, default: '' },
})

const emit = defineEmits(['stock'])

const uid = useId()
const batches = ref([])
const formOpen = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blankPrize = () => ({ name: '', value: '', count: '1', card: null })
const form = reactive({ name: '', prizes: [blankPrize()] })

async function load() {
  try {
    batches.value = await adminShopApi.mysteryBatches(props.productId)
  } catch (exception) {
    error.value = exception.message
  }
}

const total = () => form.prizes.reduce((sum, prize) => sum + (Number(prize.count) || 0), 0)

async function save() {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    batches.value = await adminShopApi.createMysteryBatch(props.productId, {
      name: form.name,
      prizes: form.prizes.map((prize) => ({
        name: prize.name,
        valueCents: eurosToCents(prize.value) ?? 0,
        count: Number(prize.count) || 0,
        cardId: prize.card?.id ?? null,
      })),
    })
    message.value = t('admin.mystery.created', { count: total() })
    Object.assign(form, { name: '', prizes: [blankPrize()] })
    formOpen.value = false
    emit('stock')
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

async function remove(batch) {
  if (!window.confirm(t('admin.mystery.deleteConfirm', { name: batch.name }))) return
  busy.value = true
  error.value = ''
  try {
    await adminShopApi.deleteMysteryBatch(batch.id)
    await load()
    emit('stock')
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

load()
</script>

<template>
  <section class="admin-panel" :aria-labelledby="`${uid}-title`">
    <h2 :id="`${uid}-title`">{{ t('admin.mystery.title') }}</h2>
    <p class="admin-intro">{{ t('admin.mystery.intro') }}</p>

    <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

    <article v-for="batch in batches" :key="batch.id" class="batch">
      <div class="batch-head">
        <h3>{{ batch.name }}</h3>
        <span class="admin-hint">
          {{ t('admin.mystery.batchState', { remaining: batch.remaining, total: batch.boxCount, date: formatDateTime(batch.createdAt) }) }}
        </span>
        <button v-if="!batch.draws.length" class="link-button danger" type="button" :disabled="busy" @click="remove(batch)">
          <Trash2 :size="14" aria-hidden="true" />
          {{ t('admin.mystery.delete') }}
        </button>
      </div>
      <div class="admin-table-scroll">
        <table class="admin-table">
          <thead>
            <tr>
              <th scope="col">{{ t('admin.mystery.contents') }}</th>
              <th scope="col" class="num">{{ t('admin.mystery.value') }}</th>
              <th scope="col" class="num">{{ t('admin.mystery.packed') }}</th>
              <th scope="col" class="num">{{ t('admin.mystery.sold') }}</th>
              <th scope="col" class="num">{{ t('admin.mystery.chanceNow') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="prize in batch.prizes" :key="prize.id">
              <th scope="row">{{ prize.name }}</th>
              <td class="num">{{ formatMoney(prize.valueCents, 'EUR') }}</td>
              <td class="num">{{ prize.count }}</td>
              <td class="num">{{ prize.drawn }}</td>
              <td class="num">{{ formatPercent(prize.chance) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <details v-if="batch.draws.length" class="draws">
        <summary>{{ t('admin.mystery.draws', { count: batch.draws.length }) }}</summary>
        <ul>
          <li v-for="draw in batch.draws" :key="`${draw.orderNumber}-${draw.drawnAt}-${draw.prizeName}`">
            <router-link :to="`/admin/bestellingen/${draw.orderNumber}`">{{ draw.orderNumber }}</router-link>:
            {{ draw.prizeName }} <span class="admin-hint">({{ formatDateTime(draw.drawnAt) }})</span>
          </li>
        </ul>
      </details>
    </article>

    <p v-if="!batches.length && !formOpen" class="admin-hint">{{ t('admin.mystery.none') }}</p>

    <button v-if="!formOpen" class="btn btn-secondary btn-small add" type="button" @click="formOpen = true">
      <PackagePlus :size="18" aria-hidden="true" />
      {{ t('admin.mystery.add') }}
    </button>

    <form v-else class="new-batch" @submit.prevent="save">
      <div class="admin-field">
        <label class="admin-label" :for="`${uid}-name`">{{ t('admin.mystery.name') }}</label>
        <input :id="`${uid}-name`" v-model="form.name" class="admin-input" required maxlength="100" :placeholder="t('admin.mystery.namePlaceholder')" />
      </div>
      <fieldset v-for="(prize, index) in form.prizes" :key="index" class="prize">
        <legend class="admin-label">{{ t('admin.mystery.kind', { number: index + 1 }) }}</legend>
        <div class="prize-fields">
          <div class="admin-field grow">
            <label class="admin-label" :for="`${uid}-p${index}-name`">{{ t('admin.mystery.contents') }}</label>
            <input :id="`${uid}-p${index}-name`" v-model="prize.name" class="admin-input" required maxlength="150" />
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="`${uid}-p${index}-value`">{{ t('admin.mystery.value') }}</label>
            <input :id="`${uid}-p${index}-value`" v-model="prize.value" class="admin-input" inputmode="decimal" required />
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="`${uid}-p${index}-count`">{{ t('admin.mystery.count') }}</label>
            <input :id="`${uid}-p${index}-count`" v-model="prize.count" class="admin-input" type="number" min="1" required />
          </div>
        </div>
        <p v-if="prize.card" class="chosen-card">
          {{ t('admin.mystery.withCard', { name: prize.card.name }) }}
          <button class="link-button" type="button" @click="prize.card = null">{{ t('admin.mystery.removeCard') }}</button>
        </p>
        <CardPicker v-else :game="game" :label="t('admin.mystery.cardLabel')" @choose="prize.card = $event" />
        <button v-if="form.prizes.length > 1" class="link-button danger" type="button" @click="form.prizes.splice(index, 1)">
          <X :size="14" aria-hidden="true" />
          {{ t('admin.mystery.removeKind') }}
        </button>
      </fieldset>
      <button class="link-button" type="button" @click="form.prizes.push(blankPrize())">{{ t('admin.mystery.addKind') }}</button>
      <p class="admin-hint total">{{ t('admin.mystery.total', { count: total() }) }}</p>
      <p class="admin-hint">{{ t('admin.mystery.lockedNote') }}</p>
      <div class="admin-actions">
        <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.mystery.create') }}</button>
        <button class="link-button" type="button" @click="formOpen = false">{{ t('admin.mystery.cancel') }}</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.message {
  margin-top: var(--space-3);
}

.batch {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-line);
}

.batch-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
}

.batch-head h3 {
  font-size: var(--text-md);
}

.batch-head .link-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-left: auto;
}

.draws {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
}

.draws summary {
  min-height: var(--touch-target);
  display: flex;
  align-items: center;
  cursor: pointer;
  font-weight: 600;
}

.draws ul {
  display: grid;
  gap: var(--space-1);
  max-height: 16rem;
  overflow-y: auto;
  padding-left: var(--space-4);
}

.add {
  margin-top: var(--space-4);
}

.new-batch {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.prize {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
}

.prize > .link-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  justify-self: start;
}

.prize-fields {
  display: grid;
  gap: var(--space-2) var(--space-3);
}

@media (min-width: 640px) {
  .prize-fields {
    grid-template-columns: minmax(0, 1fr) 8rem 6rem;
  }
}

.chosen-card {
  font-size: var(--text-sm);
}

.total {
  font-weight: 700;
}

.new-batch > .link-button {
  justify-self: start;
}
</style>
