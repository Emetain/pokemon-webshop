<script setup>
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Subtotal, shipping and total of a cart or an order. All amounts are euros including VAT. */
defineProps({
  subtotalCents: { type: Number, required: true },
  shippingCents: { type: Number, default: null },
  totalCents: { type: Number, required: true },
  vatCents: { type: Number, required: true },
  country: { type: String, default: null },
  refundedCents: { type: Number, default: 0 },
})
</script>

<template>
  <dl class="totals">
    <div class="row">
      <dt>{{ t('cart.subtotal') }}</dt>
      <dd>{{ formatMoney(subtotalCents, 'EUR') }}</dd>
    </div>
    <div v-if="shippingCents !== null" class="row">
      <dt>{{ country ? t('cart.shippingTo', { country: t(`countries.${country}`) }) : t('cart.shipping') }}</dt>
      <dd :class="{ free: shippingCents === 0 }">
        {{ shippingCents === 0 ? t('cart.free') : formatMoney(shippingCents, 'EUR') }}
      </dd>
    </div>
    <div class="row total">
      <dt>{{ t('cart.total') }}</dt>
      <dd>{{ formatMoney(totalCents, 'EUR') }}</dd>
    </div>
    <p class="vat">{{ t('cart.vat', { amount: formatMoney(vatCents, 'EUR') }) }}</p>
    <div v-if="refundedCents > 0" class="row refunded">
      <dt>{{ t('orders.detail.refunded') }}</dt>
      <dd>−{{ formatMoney(refundedCents, 'EUR') }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.totals {
  display: grid;
  gap: var(--space-1);
  margin: 0;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--text-sm);
}

.row dd {
  margin: 0;
  font-weight: 600;
  white-space: nowrap;
}

.free {
  color: var(--color-success);
}

.total {
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-line);
  font-size: var(--text-lg);
}

.total dt,
.total dd {
  font-weight: 750;
}

.vat {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  text-align: right;
}

.refunded {
  color: var(--color-success);
}
</style>
