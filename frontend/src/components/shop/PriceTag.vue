<script setup>
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * A shop price: always euros including VAT. A sale shows the old price struck through. A product that can't be
 * bought shows no price at all: its badge already says "Uitverkocht".
 */
defineProps({
  product: { type: Object, required: true },
  large: { type: Boolean, default: false },
})
</script>

<template>
  <p v-if="product.canBuy" class="price-tag" :class="{ large }">
    <span class="current" :class="{ sale: product.salePriceCents !== null }">
      {{ formatMoney(product.shownPriceCents, 'EUR') }}
    </span>
    <del v-if="product.salePriceCents !== null" class="old">
      <span class="visually-hidden">{{ t('shop.was') }}</span>
      {{ formatMoney(product.priceCents, 'EUR') }}
    </del>
    <span class="vat">{{ t('shop.inclVat') }}</span>
  </p>
</template>

<style scoped>
.price-tag {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 var(--space-2);
}

.current {
  font-size: var(--text-md);
  font-weight: 750;
}

.current.sale {
  color: var(--color-sale);
}

.old {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.vat {
  flex-basis: 100%;
  color: var(--color-ink-soft);
  font-size: var(--text-2xs);
}

.large .current {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-stretch: 88%;
}

.large .old {
  font-size: var(--text-md);
}

.large .vat {
  font-size: var(--text-xs);
}
</style>
