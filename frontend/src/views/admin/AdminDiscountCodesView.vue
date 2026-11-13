<script setup>
import { reactive, ref, useId } from 'vue'
import { Plus } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { GAMES } from '../../data/games'
import { PRODUCT_TYPES, centsToEuros, eurosToCents, typeLabel } from '../../data/shop'
import { adminContentApi } from '../../services/api'
import { fromLocalInput, toLocalInput } from '../../utils/admin'
import { formatDateTime, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

const uid = useId()
const field = (name) => `${uid}-${name}`

const codes = ref([])
const editingId = ref(null)
const formOpen = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')

const blank = () => ({
  code: '',
  kind: 'PERCENTAGE',
  percent: '10',
  amount: '',
  minimum: '',
  startsAt: '',
  endsAt: '',
  maxUses: '',
  oncePerCustomer: false,
  appliesToDrops: false,
  active: true,
  note: '',
  games: [],
  productTypes: [],
})
const form = reactive(blank())

async function load() {
  try {
    codes.value = await adminContentApi.discountCodes()
  } catch (exception) {
    error.value = exception.message
  }
}

function startNew() {
  Object.assign(form, blank())
  editingId.value = null
  formOpen.value = true
  message.value = ''
}

function startEdit(code) {
  Object.assign(form, {
    code: code.code,
    kind: code.kind,
    percent: code.percent?.toString() ?? '',
    amount: centsToEuros(code.amountCents),
    minimum: centsToEuros(code.minimumOrderCents),
    startsAt: toLocalInput(code.startsAt),
    endsAt: toLocalInput(code.endsAt),
    maxUses: code.maxUses?.toString() ?? '',
    oncePerCustomer: code.oncePerCustomer,
    appliesToDrops: code.appliesToDrops,
    active: code.active,
    note: code.note ?? '',
    games: [...code.games],
    productTypes: [...code.productTypes],
  })
  editingId.value = code.id
  formOpen.value = true
  message.value = ''
}

async function run(action, done) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
    await load()
    if (done) message.value = done
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const number = (value) => (String(value).trim() ? Number(value) : null)

function save() {
  const body = {
    code: form.code,
    kind: form.kind,
    percent: form.kind === 'PERCENTAGE' ? number(form.percent) : null,
    amountCents: form.kind === 'FIXED_AMOUNT' ? eurosToCents(form.amount) : null,
    minimumOrderCents: eurosToCents(form.minimum),
    startsAt: fromLocalInput(form.startsAt),
    endsAt: fromLocalInput(form.endsAt),
    maxUses: number(form.maxUses),
    oncePerCustomer: form.oncePerCustomer,
    appliesToDrops: form.appliesToDrops,
    active: form.active,
    note: form.note || null,
    games: form.games,
    productTypes: form.productTypes,
  }
  run(async () => {
    if (editingId.value) await adminContentApi.updateDiscountCode(editingId.value, body)
    else await adminContentApi.createDiscountCode(body)
    formOpen.value = false
  }, t('admin.discounts.saved'))
}

function remove(code) {
  if (!window.confirm(t('admin.discounts.deleteConfirm', { code: code.code }))) return
  run(() => adminContentApi.deleteDiscountCode(code.id), t('admin.discounts.deleted'))
}

function amountText(code) {
  return code.kind === 'PERCENTAGE' ? `${code.percent}%` : formatMoney(code.amountCents, 'EUR')
}

/** What the code is limited to, in one line. */
function limitsText(code) {
  const parts = []
  if (code.minimumOrderCents) parts.push(t('admin.discounts.fromAmount', { amount: formatMoney(code.minimumOrderCents, 'EUR') }))
  if (code.games.length) parts.push(code.games.map((slug) => t(GAMES.find((game) => game.slug === slug)?.labelKey ?? slug)).join(', '))
  if (code.productTypes.length) parts.push(code.productTypes.map(typeLabel).join(', '))
  if (code.oncePerCustomer) parts.push(t('admin.discounts.oncePerCustomerShort'))
  if (code.appliesToDrops) parts.push(t('admin.discounts.dropsShort'))
  return parts.join('; ') || t('admin.discounts.everything')
}

function periodText(code) {
  if (!code.startsAt && !code.endsAt) return t('admin.discounts.always')
  return [code.startsAt && formatDateTime(code.startsAt), code.endsAt && formatDateTime(code.endsAt)]
    .map((value) => value || '…')
    .join(' – ')
}

load()
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.discounts.title') }}</h1>
      <p class="admin-intro">{{ t('admin.discounts.intro') }}</p>
    </div>
    <button class="btn btn-primary btn-small" type="button" @click="startNew">
      <Plus :size="18" aria-hidden="true" />
      {{ t('admin.discounts.add') }}
    </button>
  </div>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form v-if="formOpen" class="admin-panel" @submit.prevent="save">
    <h2>{{ editingId ? t('admin.discounts.edit') : t('admin.discounts.add') }}</h2>
    <div class="admin-fields">
      <div class="admin-field">
        <label class="admin-label" :for="field('code')">{{ t('admin.discounts.code') }}</label>
        <input :id="field('code')" v-model="form.code" class="admin-input code" required maxlength="40" autocomplete="off" />
        <p class="admin-hint">{{ t('admin.discounts.codeHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('kind')">{{ t('admin.discounts.kind') }}</label>
        <select :id="field('kind')" v-model="form.kind" class="admin-input">
          <option value="PERCENTAGE">{{ t('admin.discounts.kinds.PERCENTAGE') }}</option>
          <option value="FIXED_AMOUNT">{{ t('admin.discounts.kinds.FIXED_AMOUNT') }}</option>
        </select>
      </div>
      <div v-if="form.kind === 'PERCENTAGE'" class="admin-field">
        <label class="admin-label" :for="field('percent')">{{ t('admin.discounts.percent') }}</label>
        <input :id="field('percent')" v-model="form.percent" class="admin-input" type="number" min="1" max="100" required />
      </div>
      <div v-else class="admin-field">
        <label class="admin-label" :for="field('amount')">{{ t('admin.discounts.amount') }}</label>
        <input :id="field('amount')" v-model="form.amount" class="admin-input" inputmode="decimal" required />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('minimum')">{{ t('admin.discounts.minimum') }}</label>
        <input :id="field('minimum')" v-model="form.minimum" class="admin-input" inputmode="decimal" />
        <p class="admin-hint">{{ t('admin.discounts.minimumHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('starts')">{{ t('admin.discounts.startsAt') }}</label>
        <input :id="field('starts')" v-model="form.startsAt" class="admin-input" type="datetime-local" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('ends')">{{ t('admin.discounts.endsAt') }}</label>
        <input :id="field('ends')" v-model="form.endsAt" class="admin-input" type="datetime-local" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="field('max')">{{ t('admin.discounts.maxUses') }}</label>
        <input :id="field('max')" v-model="form.maxUses" class="admin-input" type="number" min="1" />
        <p class="admin-hint">{{ t('admin.discounts.maxUsesHint') }}</p>
      </div>
      <div class="admin-field wide">
        <label class="admin-label" :for="field('note')">{{ t('admin.discounts.note') }}</label>
        <input :id="field('note')" v-model="form.note" class="admin-input" maxlength="200" />
      </div>
      <fieldset class="admin-field wide choices">
        <legend class="admin-label">{{ t('admin.discounts.games') }}</legend>
        <label v-for="game in GAMES" :key="game.slug" class="admin-check">
          <input v-model="form.games" type="checkbox" :value="game.slug" />
          {{ t(game.labelKey) }}
        </label>
        <p class="admin-hint full">{{ t('admin.discounts.allWhenEmpty') }}</p>
      </fieldset>
      <fieldset class="admin-field wide choices">
        <legend class="admin-label">{{ t('admin.discounts.types') }}</legend>
        <label v-for="type in PRODUCT_TYPES" :key="type.type" class="admin-check">
          <input v-model="form.productTypes" type="checkbox" :value="type.type" />
          {{ typeLabel(type.type) }}
        </label>
        <p class="admin-hint full">{{ t('admin.discounts.allWhenEmpty') }}</p>
      </fieldset>
      <label class="admin-check">
        <input v-model="form.oncePerCustomer" type="checkbox" />
        {{ t('admin.discounts.oncePerCustomer') }}
      </label>
      <label class="admin-check">
        <input v-model="form.appliesToDrops" type="checkbox" />
        {{ t('admin.discounts.appliesToDrops') }}
      </label>
      <label class="admin-check">
        <input v-model="form.active" type="checkbox" />
        {{ t('admin.discounts.active') }}
      </label>
    </div>
    <p class="admin-hint">{{ t('admin.discounts.shippingNote') }}</p>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('admin.discounts.save') }}</button>
      <button class="link-button" type="button" @click="formOpen = false">{{ t('admin.discounts.cancel') }}</button>
    </div>
  </form>

  <div v-if="codes.length" class="admin-table-scroll">
    <table class="admin-table">
      <thead>
        <tr>
          <th scope="col">{{ t('admin.discounts.code') }}</th>
          <th scope="col">{{ t('admin.discounts.discount') }}</th>
          <th scope="col">{{ t('admin.discounts.limits') }}</th>
          <th scope="col">{{ t('admin.discounts.period') }}</th>
          <th scope="col" class="num">{{ t('admin.discounts.uses') }}</th>
          <th scope="col" class="num">{{ t('admin.discounts.given') }}</th>
          <th scope="col">{{ t('admin.discounts.state') }}</th>
          <th scope="col"><span class="visually-hidden">{{ t('admin.discounts.actions') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="code in codes" :key="code.id">
          <th scope="row">
            <span class="code">{{ code.code }}</span>
            <span v-if="code.note" class="admin-hint block">{{ code.note }}</span>
          </th>
          <td>{{ amountText(code) }}</td>
          <td>{{ limitsText(code) }}</td>
          <td>{{ periodText(code) }}</td>
          <td class="num">{{ code.paidUses }}{{ code.maxUses ? ` / ${code.maxUses}` : '' }}</td>
          <td class="num">{{ formatMoney(code.givenCents, 'EUR') }}</td>
          <td><span class="state" :class="code.state.toLowerCase()">{{ t(`admin.discounts.states.${code.state}`) }}</span></td>
          <td class="actions">
            <button class="link-button" type="button" @click="startEdit(code)">{{ t('admin.discounts.edit') }}</button>
            <button v-if="!code.uses" class="link-button danger" type="button" @click="remove(code)">
              {{ t('admin.discounts.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="!formOpen" class="admin-intro empty">{{ t('admin.discounts.empty') }}</p>
  <p class="admin-hint usesHint">{{ t('admin.discounts.usesHint') }}</p>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.message {
  margin-top: var(--space-3);
}

.admin-panel {
  max-width: 60rem;
}

.choices {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-4);
  margin: 0;
  padding: 0;
  border: 0;
}

.choices legend {
  float: left;
  width: 100%;
}

.full {
  width: 100%;
}

.code {
  font-weight: 700;
  letter-spacing: 0.02em;
}

input.code {
  text-transform: uppercase;
}

.block {
  display: block;
  font-weight: 400;
}

.state {
  font-weight: 700;
}

.state.active {
  color: var(--color-success);
}

.state.scheduled {
  color: var(--color-warning);
}

.state.off,
.state.expired,
.state.used_up {
  color: var(--color-ink-soft);
}

.actions {
  white-space: nowrap;
}

.actions .link-button + .link-button {
  margin-left: var(--space-3);
}

.empty {
  margin-top: var(--space-5);
}

.usesHint {
  margin-top: var(--space-3);
}
</style>
