<script setup>
import { ref, useId, watch } from 'vue'
import FormMessage from '../FormMessage.vue'
import { adminShopApi } from '../../services/api'
import { reasonLabel, signed } from '../../utils/admin'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const REASONS = ['PURCHASE', 'RETURN', 'CORRECTION']

/**
 * The stock of one product: change it with a reason, set a counted number, and the log of every change.
 * The log pages with buttons, so its page doesn't end up in the URL of the product form.
 */
const props = defineProps({
  productId: { type: Number, required: true },
  stock: { type: Object, required: true },
})

const emit = defineEmits(['change'])

const ids = { change: useId(), reason: useId(), note: useId(), count: useId(), countNote: useId(), title: useId() }

const change = ref('')
const reason = ref('PURCHASE')
const note = ref('')
const count = ref('')
const countNote = ref('')
const busy = ref(false)
const message = ref('')
const error = ref('')
const movements = ref(null)
const page = ref(0)

async function loadMovements() {
  try {
    movements.value = await adminShopApi.movements(props.productId, page.value)
  } catch (exception) {
    error.value = exception.message
  }
}

async function submit(action, done) {
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    const result = await action()
    emit('change', result.stock)
    message.value = done(result)
    page.value = 0
    await loadMovements()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function adjust() {
  const amount = Number.parseInt(change.value, 10)
  if (!Number.isFinite(amount)) {
    error.value = t('admin.stock.changeHint')
    return
  }
  submit(
    () => adminShopApi.adjustStock(props.productId, { change: amount, reason: reason.value, note: note.value || null }),
    () => {
      change.value = ''
      note.value = ''
      return t('admin.stock.done')
    },
  )
}

function setCount() {
  const amount = Number.parseInt(count.value, 10)
  if (!Number.isFinite(amount) || amount < 0) {
    error.value = t('admin.stock.countHint')
    return
  }
  submit(
    () => adminShopApi.countStock(props.productId, { onHand: amount, note: countNote.value || null }),
    (result) => {
      count.value = ''
      countNote.value = ''
      return result.movement ? t('admin.stock.done') : t('admin.stock.unchanged', { count: amount })
    },
  )
}

function goTo(number) {
  page.value = number
  loadMovements()
}

watch(() => props.productId, () => {
  page.value = 0
  loadMovements()
}, { immediate: true })
</script>

<template>
  <section class="admin-panel" :aria-labelledby="ids.title">
    <h2 :id="ids.title">{{ t('admin.stock.panel') }}</h2>
    <dl class="numbers">
      <div>
        <dt>{{ t('admin.stock.onHand') }}</dt>
        <dd>{{ formatNumber(stock.onHand) }}</dd>
      </div>
      <div>
        <dt>{{ t('admin.stock.reserved') }}</dt>
        <dd>{{ formatNumber(stock.reserved) }}</dd>
      </div>
      <div :class="{ low: stock.low && stock.onHand > 0 }">
        <dt>{{ t('admin.stock.available') }}</dt>
        <dd>{{ formatNumber(stock.available) }}</dd>
      </div>
    </dl>

    <div class="forms">
      <form class="stock-form" @submit.prevent="adjust">
        <h3 class="form-title">{{ t('admin.stock.adjust') }}</h3>
        <div class="admin-fields">
          <div class="admin-field">
            <label class="admin-label" :for="ids.change">{{ t('admin.stock.change') }}</label>
            <input :id="ids.change" v-model="change" class="admin-input" inputmode="numeric" required />
            <p class="admin-hint">{{ t('admin.stock.changeHint') }}</p>
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="ids.reason">{{ t('admin.stock.reason') }}</label>
            <select :id="ids.reason" v-model="reason" class="admin-input">
              <option v-for="option in REASONS" :key="option" :value="option">{{ reasonLabel(option) }}</option>
            </select>
          </div>
          <div class="admin-field wide">
            <label class="admin-label" :for="ids.note">{{ t('admin.stock.note') }}</label>
            <input :id="ids.note" v-model="note" class="admin-input" maxlength="500" />
            <p class="admin-hint">{{ t('admin.stock.noteHint') }}</p>
          </div>
        </div>
        <div class="admin-actions">
          <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.stock.submit') }}</button>
        </div>
      </form>

      <form class="stock-form" @submit.prevent="setCount">
        <h3 class="form-title">{{ t('admin.stock.count') }}</h3>
        <p class="admin-hint">{{ t('admin.stock.countHint') }}</p>
        <div class="admin-fields">
          <div class="admin-field">
            <label class="admin-label" :for="ids.count">{{ t('admin.stock.onHand') }}</label>
            <input :id="ids.count" v-model="count" class="admin-input" inputmode="numeric" required />
          </div>
          <div class="admin-field">
            <label class="admin-label" :for="ids.countNote">{{ t('admin.stock.note') }}</label>
            <input :id="ids.countNote" v-model="countNote" class="admin-input" maxlength="500" />
          </div>
        </div>
        <div class="admin-actions">
          <button class="btn btn-secondary btn-small" type="submit" :disabled="busy">{{ t('admin.stock.countSubmit') }}</button>
        </div>
      </form>
    </div>

    <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

    <h3 class="form-title history-title">{{ t('admin.stock.history') }}</h3>
    <p v-if="movements && !movements.items.length" class="admin-hint">{{ t('admin.stock.historyEmpty') }}</p>
    <div v-else-if="movements" class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th scope="col">{{ t('admin.stock.when') }}</th>
            <th scope="col">{{ t('admin.stock.what') }}</th>
            <th class="num" scope="col">{{ t('admin.stock.change') }}</th>
            <th scope="col">{{ t('admin.stock.after') }}</th>
            <th scope="col">{{ t('admin.stock.who') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="movement in movements.items" :key="movement.id">
            <td class="when">{{ formatDateTime(movement.createdAt) }}</td>
            <td>
              {{ reasonLabel(movement.reason) }}
              <span v-if="movement.note" class="note">{{ movement.note }}</span>
            </td>
            <td class="num" :class="{ plus: movement.change > 0, minus: movement.change < 0 }">{{ signed(movement.change) }}</td>
            <td class="after">
              {{ t('admin.stock.afterValue', { onHand: movement.onHandAfter, reserved: movement.reservedAfter }) }}
            </td>
            <td>{{ movement.adminName ?? t('admin.stock.shop') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="movements && movements.totalPages > 1" class="admin-actions">
      <button class="link-button" type="button" :disabled="page === 0" @click="goTo(page - 1)">
        {{ t('catalog.pagination.previous') }}
      </button>
      <span class="admin-hint">{{ t('catalog.pagination.page', { page: page + 1 }) }} / {{ movements.totalPages }}</span>
      <button class="link-button" type="button" :disabled="page + 1 >= movements.totalPages" @click="goTo(page + 1)">
        {{ t('catalog.pagination.next') }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.numbers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3) var(--space-6);
  margin: var(--space-3) 0 0;
}

.numbers dt {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 600;
}

.numbers dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-stretch: 88%;
  font-weight: 750;
}

.numbers .low dd {
  color: var(--color-warning);
}

.forms {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-4);
}

.form-title {
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-stretch: 100%;
  font-weight: 700;
}

.history-title {
  margin-top: var(--space-5);
}

.message {
  margin-top: var(--space-3);
}

.when,
.after {
  white-space: nowrap;
}

.note {
  display: block;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.plus {
  color: var(--color-success);
  font-weight: 700;
}

.minus {
  color: var(--color-danger);
  font-weight: 700;
}

@media (min-width: 900px) {
  .forms {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
