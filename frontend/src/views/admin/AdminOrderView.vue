<script setup>
import { computed, reactive, ref, useId, watch } from 'vue'
import { ChevronLeft, Printer } from 'lucide-vue-next'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import FormMessage from '../../components/FormMessage.vue'
import OrderDetails from '../../components/order/OrderDetails.vue'
import OrderStatusBadge from '../../components/order/OrderStatusBadge.vue'
import { eurosToCents } from '../../data/shop'
import { adminOrderApi } from '../../services/api'
import { formatDateTime, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  number: { type: String, required: true },
})

const CARRIERS = ['POSTNL', 'DHL', 'BPOST', 'DPD', 'OTHER']
const ids = { note: useId(), carrier: useId(), code: useId(), url: useId(), reason: useId(), amount: useId(), refundNote: useId() }

const detail = ref(null)
const loading = ref(true)
const loadError = ref('')
const busy = ref(false)
const error = ref('')
const done = ref('')

const statusNote = ref('')
const shipment = reactive({ carrier: 'POSTNL', code: '', url: '' })
const cancelReason = ref('')
const refund = reactive({ amount: '', restock: false, note: '' })

const order = computed(() => detail.value?.order)
const actions = computed(() => detail.value?.actions)

function fill(result) {
  detail.value = result
  shipment.carrier = result.order.carrier ?? 'POSTNL'
  shipment.code = result.order.trackingCode ?? ''
  shipment.url = result.order.carrier && result.order.carrier !== 'POSTNL' ? (result.order.trackingUrl ?? '') : ''
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    fill(await adminOrderApi.get(props.number))
  } catch (exception) {
    loadError.value = exception.message
  } finally {
    loading.value = false
  }
}

/** Returns whether it worked, so a form only empties after a success. */
async function run(call) {
  error.value = ''
  done.value = ''
  busy.value = true
  try {
    fill(await call())
    done.value = t('admin.orders.done')
    return true
  } catch (exception) {
    error.value = exception.message
    return false
  } finally {
    busy.value = false
  }
}

async function setStatus(status) {
  if (await run(() => adminOrderApi.setStatus(props.number, { status, note: statusNote.value || null }))) statusNote.value = ''
}

const ship = () =>
  run(() => adminOrderApi.ship(props.number, { carrier: shipment.carrier, code: shipment.code, url: shipment.url || null }))

function cancel() {
  const pending = order.value.status === 'PENDING_PAYMENT'
  const question = pending
    ? t('admin.orders.cancelPendingConfirm', { number: order.value.number })
    : t('admin.orders.cancelConfirm', { number: order.value.number, amount: formatMoney(actions.value.refundableCents, 'EUR') })
  if (!window.confirm(question)) return
  run(() => adminOrderApi.cancel(props.number, { note: cancelReason.value || null })).then((ok) => {
    if (ok) cancelReason.value = ''
  })
}

function payBack() {
  const amountCents = refund.amount.trim() ? eurosToCents(refund.amount) : null
  if (refund.amount.trim() && amountCents === null) {
    error.value = t('admin.orders.refundAmountHint')
    return
  }
  const shown = formatMoney(amountCents ?? actions.value.refundableCents, 'EUR')
  if (!window.confirm(t('admin.orders.refundConfirm', { amount: shown }))) return
  run(() => adminOrderApi.refund(props.number, { amountCents, restock: refund.restock, note: refund.note || null })).then((ok) => {
    if (!ok) return
    refund.amount = ''
    refund.note = ''
    refund.restock = false
  })
}

watch(() => props.number, load, { immediate: true })
</script>

<template>
  <router-link class="back" to="/admin/bestellingen">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ t('admin.orders.back') }}
  </router-link>

  <p v-if="loading" class="muted">{{ t('admin.orders.loading') }}</p>
  <CatalogNotice v-else-if="loadError" :title="loadError" />

  <template v-else>
    <header class="head">
      <div>
        <h1 class="title">
          {{ t('admin.orders.detailTitle', { number: order.number }) }}
          <OrderStatusBadge :status="order.status" />
        </h1>
        <p class="admin-intro">{{ t('admin.orders.placed', { date: formatDateTime(order.createdAt) }) }}</p>
      </div>
      <router-link class="btn btn-secondary btn-small" :to="`/admin/bestellingen/${order.number}/pakbon`" target="_blank">
        <Printer :size="18" aria-hidden="true" />
        {{ t('admin.orders.packingSlip') }}
      </router-link>
    </header>

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
    <FormMessage v-if="done" class="message" type="success">{{ done }}</FormMessage>

    <div class="facts">
      <section class="admin-panel fact">
        <h2>{{ t('admin.orders.customer') }}</h2>
        <p class="strong">{{ detail.customerName }}</p>
        <p><a :href="`mailto:${order.email}`">{{ order.email }}</a></p>
        <p class="muted">{{ detail.userId ? t('admin.orders.account') : t('admin.orders.guestOrder') }}</p>
      </section>
      <section class="admin-panel fact">
        <h2>{{ t('admin.orders.payment') }}</h2>
        <p v-if="order.paidAt">
          {{ t('orders.detail.paidWith', { method: order.paymentMethod ? t(`cart.paymentMethods.${order.paymentMethod}`) : 'Stripe', date: formatDateTime(order.paidAt) }) }}
        </p>
        <p v-else>{{ t('orders.detail.notPaid') }}</p>
        <p v-if="detail.stripePaymentIntentId" class="muted code">{{ t('admin.orders.stripe') }}: {{ detail.stripePaymentIntentId }}</p>
        <p v-else-if="detail.stripeSessionId" class="muted code">{{ t('admin.orders.stripe') }}: {{ detail.stripeSessionId }}</p>
      </section>
      <section class="admin-panel fact">
        <h2>{{ t('admin.orders.stock') }}</h2>
        <p>{{ t(`admin.orders.stockStates.${detail.stockState}`, { date: detail.reservedUntil ? formatDateTime(detail.reservedUntil) : '' }) }}</p>
      </section>
    </div>

    <div class="layout">
      <OrderDetails :order="order" admin />

      <div class="side">
        <section v-if="actions.statuses.length" class="admin-panel">
          <h2>{{ t('admin.orders.status') }}</h2>
          <div class="admin-field">
            <label class="admin-label" :for="ids.note">{{ t('admin.orders.statusNote') }}</label>
            <input :id="ids.note" v-model="statusNote" class="admin-input" maxlength="300" />
          </div>
          <div class="admin-actions">
            <button
              v-for="status in actions.statuses"
              :key="status"
              class="btn btn-secondary btn-small"
              type="button"
              :disabled="busy"
              @click="setStatus(status)"
            >
              {{ t('admin.orders.setStatus', { status: t(`orders.statuses.${status}`) }) }}
            </button>
          </div>
        </section>

        <section v-if="actions.canShip" class="admin-panel">
          <h2>{{ order.status === 'SHIPPED' ? t('admin.orders.shipAgain') : t('admin.orders.ship') }}</h2>
          <p class="admin-hint">{{ t('admin.orders.shipIntro') }}</p>
          <form class="admin-fields" @submit.prevent="ship">
            <div class="admin-field">
              <label class="admin-label" :for="ids.carrier">{{ t('admin.orders.carrier') }}</label>
              <select :id="ids.carrier" v-model="shipment.carrier" class="admin-input">
                <option v-for="carrier in CARRIERS" :key="carrier" :value="carrier">{{ t(`orders.carriers.${carrier}`) }}</option>
              </select>
            </div>
            <div class="admin-field">
              <label class="admin-label" :for="ids.code">{{ t('admin.orders.code') }}</label>
              <input :id="ids.code" v-model="shipment.code" class="admin-input" required maxlength="100" autocomplete="off" />
            </div>
            <div class="admin-field wide">
              <label class="admin-label" :for="ids.url">{{ t('admin.orders.url') }}</label>
              <input :id="ids.url" v-model="shipment.url" class="admin-input" type="url" maxlength="500" placeholder="https://" />
              <p class="admin-hint">{{ t('admin.orders.urlHint') }}</p>
            </div>
            <div class="admin-field wide">
              <button class="btn btn-primary btn-small" type="submit" :disabled="busy || !shipment.code.trim()">
                {{ t('admin.orders.shipButton') }}
              </button>
            </div>
          </form>
        </section>

        <section v-if="actions.canRefund" class="admin-panel">
          <h2>{{ t('admin.orders.refund') }}</h2>
          <p class="admin-hint">{{ t('admin.orders.refundIntro', { amount: formatMoney(actions.refundableCents, 'EUR') }) }}</p>
          <form class="admin-fields" @submit.prevent="payBack">
            <div class="admin-field">
              <label class="admin-label" :for="ids.amount">{{ t('admin.orders.refundAmount') }}</label>
              <input :id="ids.amount" v-model="refund.amount" class="admin-input" inputmode="decimal" autocomplete="off" />
              <p class="admin-hint">{{ t('admin.orders.refundAmountHint') }}</p>
            </div>
            <div class="admin-field wide">
              <label class="admin-label" :for="ids.refundNote">{{ t('admin.orders.cancelReason') }}</label>
              <input :id="ids.refundNote" v-model="refund.note" class="admin-input" maxlength="300" />
            </div>
            <label v-if="actions.canRestock" class="admin-check admin-field wide">
              <input v-model="refund.restock" type="checkbox" />
              {{ t('admin.orders.restock') }}
            </label>
            <div class="admin-field wide">
              <button class="btn btn-secondary btn-small" type="submit" :disabled="busy">{{ t('admin.orders.refundButton') }}</button>
            </div>
          </form>
        </section>

        <section v-if="actions.canCancel" class="admin-panel">
          <h2>{{ t('admin.orders.cancel') }}</h2>
          <p class="admin-hint">{{ t('admin.orders.cancelIntro') }}</p>
          <div class="admin-field">
            <label class="admin-label" :for="ids.reason">{{ t('admin.orders.cancelReason') }}</label>
            <input :id="ids.reason" v-model="cancelReason" class="admin-input" maxlength="300" />
          </div>
          <div class="admin-actions">
            <button class="btn btn-secondary btn-small danger" type="button" :disabled="busy" @click="cancel">
              {{ t('admin.orders.cancelButton') }}
            </button>
          </div>
        </section>

        <section class="admin-panel">
          <h2>{{ t('admin.orders.history') }}</h2>
          <ol class="history">
            <li v-for="(event, index) in detail.history" :key="index" class="event">
              <span class="event-head">
                <OrderStatusBadge :status="event.status" />
                <time :datetime="event.at">{{ formatDateTime(event.at) }}</time>
              </span>
              <span v-if="event.note" class="event-note">{{ event.note }}</span>
              <span v-if="event.adminName" class="muted small">{{ t('admin.orders.by', { name: event.adminName }) }}</span>
            </li>
          </ol>
        </section>
      </div>
    </div>
  </template>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.message {
  margin-top: var(--space-3);
}

.muted {
  color: var(--color-ink-soft);
}

.small {
  font-size: var(--text-xs);
}

.strong {
  font-weight: 650;
}

.code {
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
  gap: var(--space-3);
}

.fact {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.layout {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-5);
}

.side {
  display: grid;
  align-content: start;
}

.side .admin-panel:first-child {
  margin-top: 0;
}

.danger {
  color: var(--color-danger);
  box-shadow: inset 0 0 0 1.5px var(--color-danger);
}

.history {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.event {
  display: grid;
  gap: var(--space-1);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.event:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.event-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.event-note {
  overflow-wrap: anywhere;
}

@media (min-width: 1200px) {
  .layout {
    grid-template-columns: minmax(0, 1fr) 24rem;
    align-items: start;
  }
}
</style>
