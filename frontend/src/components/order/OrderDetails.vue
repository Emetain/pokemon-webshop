<script setup>
import { computed } from 'vue'
import { Truck } from 'lucide-vue-next'
import CartTotals from '../cart/CartTotals.vue'
import OrderTimeline from './OrderTimeline.vue'
import ProductImage from '../shop/ProductImage.vue'
import { formatDate, formatDateTime, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** An order for its customer: products, totals, address, payment, shipping and the road it took. */
const props = defineProps({
  order: { type: Object, required: true },
  /** Product links only work in the shop, not on a printed or admin page. */
  linkProducts: { type: Boolean, default: true },
  /** On the admin page: one column, without the steps and the payment, which the admin sees in more detail there. */
  admin: { type: Boolean, default: false },
})

const paymentText = computed(() => {
  const order = props.order
  if (!order.paidAt) return t('orders.detail.notPaid')
  const method = order.paymentMethod ? t(`cart.paymentMethods.${order.paymentMethod}`) : 'Stripe'
  return t('orders.detail.paidWith', { method, date: formatDateTime(order.paidAt) })
})

const shipmentText = computed(() => {
  const order = props.order
  if (!order.hasPreOrders || !order.hasInStock) return ''
  return order.shipmentPreference === 'IN_STOCK_FIRST' ? t('orders.detail.inStockFirst') : t('orders.detail.together')
})
</script>

<template>
  <div class="details" :class="{ stacked: admin }">
    <section class="block products" :aria-label="t('orders.detail.products')">
      <ul class="lines">
        <li v-for="line in order.lines" :key="`${line.productId}-${line.unitPriceCents}`" class="line">
          <ProductImage class="image" :src="line.imageUrl" :is-card="line.imageIsCard" :type="line.type" />
          <div class="line-body">
            <router-link v-if="linkProducts" class="name" :to="{ name: 'product', params: { slug: line.slug } }">{{ line.name }}</router-link>
            <span v-else class="name">{{ line.name }}</span>
            <span class="meta">{{ line.quantity }} × {{ formatMoney(line.unitPriceCents, 'EUR') }}</span>
            <span v-if="line.preOrder" class="pre-order">
              {{ line.shipsFrom ? t('orders.detail.expected', { date: formatDate(line.shipsFrom) }) : t('shop.badges.preOrder') }}
            </span>
          </div>
          <span class="amount">{{ formatMoney(line.lineTotalCents, 'EUR') }}</span>
        </li>
      </ul>
      <CartTotals
        class="totals"
        :subtotal-cents="order.subtotalCents"
        :shipping-cents="order.shippingCents"
        :total-cents="order.totalCents"
        :vat-cents="order.vatCents"
        :country="order.address.country"
        :refunded-cents="order.refundedCents"
      />
    </section>

    <aside class="side">
      <section v-if="!admin" class="block">
        <h2 class="block-title">{{ t('orders.detail.history') }}</h2>
        <OrderTimeline :timeline="order.timeline" :status="order.status" />
      </section>

      <section v-if="order.trackingCode" class="block tracking">
        <h2 class="block-title">{{ t('orders.detail.tracking') }}</h2>
        <p>{{ t('orders.detail.trackingWith', { carrier: t(`orders.carriers.${order.carrier}`), code: order.trackingCode }) }}</p>
        <a v-if="order.trackingUrl" class="btn btn-primary follow" :href="order.trackingUrl" target="_blank" rel="noopener noreferrer">
          <Truck :size="18" aria-hidden="true" />
          {{ t('orders.detail.follow') }}
        </a>
      </section>

      <section class="block">
        <h2 class="block-title">{{ t('orders.detail.address') }}</h2>
        <address class="address">
          {{ order.address.name }}<br />
          {{ order.address.street }} {{ order.address.houseNumber }}<br />
          {{ order.address.postcode }} {{ order.address.city }}<br />
          {{ t(`countries.${order.address.country}`) }}
        </address>
        <p v-if="order.deliveryTime" class="note">{{ t('cart.deliveryTime', { time: order.deliveryTime }) }}</p>
        <p v-if="shipmentText" class="note">{{ shipmentText }}</p>
      </section>

      <section v-if="!admin" class="block">
        <h2 class="block-title">{{ t('orders.detail.payment') }}</h2>
        <p class="note">{{ paymentText }}</p>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.details {
  display: grid;
  gap: var(--space-5);
}

.block-title {
  margin-bottom: var(--space-3);
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-stretch: 100%;
  font-weight: 700;
}

.lines {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-line);
}

.line {
  display: grid;
  grid-template-columns: 4rem minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: start;
  padding-block: var(--space-3);
  border-bottom: 1px solid var(--color-line);
}

.line-body {
  display: grid;
  gap: var(--space-half);
  min-width: 0;
}

.name {
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
  text-decoration: none;
  overflow-wrap: anywhere;
}

a.name:hover {
  color: var(--color-brand);
  text-decoration: underline;
}

.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.pre-order {
  color: var(--color-brand-deep);
  font-size: var(--text-xs);
  font-weight: 600;
}

.amount {
  font-size: var(--text-sm);
  font-weight: 700;
  white-space: nowrap;
}

.totals {
  max-width: 22rem;
  margin: var(--space-4) 0 0 auto;
}

.side {
  display: grid;
  align-content: start;
  gap: var(--space-5);
}

.side .block {
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-line);
}

.side .block:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.tracking p {
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.follow {
  margin-top: var(--space-3);
}

.address {
  font-size: var(--text-sm);
  font-style: normal;
  overflow-wrap: anywhere;
}

.note {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

@media (min-width: 900px) {
  .details:not(.stacked) {
    grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
    gap: var(--space-7);
  }
}
</style>
