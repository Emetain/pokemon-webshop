<script setup>
import { computed } from 'vue'
import { Minus, Plus } from 'lucide-vue-next'
import ProductImage from '../shop/ProductImage.vue'
import { findGame } from '../../data/games'
import { MAX_PER_LINE } from '../../data/shop'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * A product in a drop: its limit, and whether it sold out. Never how many there are. The price only shows once it is
 * this person's turn: then they get a counter to put it in the drop cart, as far as the limits allow. The drop cart
 * doesn't look at what is left: the checkout says when there isn't enough, without the number.
 */
const props = defineProps({
  product: { type: Object, required: true },
  /** The drop is live and it is this person's turn. */
  shopping: { type: Boolean, default: false },
  /** From the drop cart, only while shopping: `priceCents` and `regularPriceCents`. */
  price: { type: Object, default: null },
  inCart: { type: Number, default: 0 },
  /** The most this product can have in the cart within the limits. Null: no limit. */
  room: { type: Number, default: null },
  ordered: { type: Number, default: 0 },
  busy: { type: Boolean, default: false },
  /** Live or ended: shows whether it sold out. */
  live: { type: Boolean, default: false },
  ended: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

const accent = computed(() => (props.product.game ? findGame(props.product.game)?.accent : null))
const max = computed(() => (props.product.allReserved ? 0 : Math.min(props.room ?? MAX_PER_LINE, MAX_PER_LINE)))
const showPrice = computed(() => props.shopping && props.price?.priceCents != null)
</script>

<template>
  <article class="drop-product" :class="{ 'sold-out': product.soldOut }">
    <div class="image">
      <ProductImage
        :src="product.imageUrl"
        :is-card="product.imageIsCard"
        :alt="product.name"
        :set-logo="product.setLogoUrl"
        :type="product.type"
        :game="accent"
      />
    </div>
    <div class="info">
      <h3 class="name">
        <router-link v-if="product.slug" :to="`/product/${product.slug}`">{{ product.name }}</router-link>
        <template v-else>{{ product.name }}</template>
      </h3>

      <template v-if="showPrice">
        <p class="price">
          <strong>{{ formatMoney(price.priceCents, 'EUR') }}</strong>
          <s v-if="price.regularPriceCents" class="regular">{{ t('drops.product.regular', { price: formatMoney(price.regularPriceCents, 'EUR') }) }}</s>
        </p>
        <p class="vat">{{ t('shop.inclVat') }}</p>
      </template>
      <p v-else-if="!shopping && !ended && !product.soldOut" class="price-later">{{ t('drops.product.priceLater') }}</p>

      <p v-if="product.maxPerCustomer" class="limit">{{ t('drops.product.limit', { count: product.maxPerCustomer }) }}</p>
      <p v-if="ordered > 0" class="limit">{{ t('drops.product.ordered', { count: ordered }) }}</p>

      <div v-if="(live && product.allReserved) || product.soldOut" class="stock" aria-live="polite">
        <p class="stock-text">{{ product.soldOut ? t('drops.product.soldOut') : t('drops.product.allReserved') }}</p>
        <p v-if="!product.soldOut" class="hint">{{ t('drops.product.allReservedHint') }}</p>
      </div>

      <div v-if="shopping && !product.soldOut" class="stepper">
        <button
          type="button"
          class="step"
          :disabled="busy || inCart === 0"
          :aria-label="t('drops.product.decrease', { name: product.name })"
          @click="emit('change', inCart - 1)"
        >
          <Minus :size="18" aria-hidden="true" />
        </button>
        <output class="count" :aria-label="t('drops.product.quantity', { name: product.name })">{{ inCart }}</output>
        <button
          type="button"
          class="step"
          :disabled="busy || inCart >= max"
          :aria-label="t('drops.product.increase', { name: product.name })"
          @click="emit('change', inCart + 1)"
        >
          <Plus :size="18" aria-hidden="true" />
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.drop-product {
  display: grid;
  grid-template-columns: 7rem minmax(0, 1fr);
  gap: var(--space-3);
  height: 100%;
  padding: var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.sold-out .image {
  opacity: 0.55;
}

.info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.name {
  font-size: var(--text-md);
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.name a {
  color: inherit;
  text-decoration: none;
}

@media (hover: hover) {
  .name a:hover {
    color: var(--color-brand);
    text-decoration: underline;
  }
}

.price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.regular {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.vat,
.limit,
.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.limit {
  font-weight: 650;
}

.price-later {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 650;
}

.stock {
  margin-top: var(--space-1);
}

.stock-text {
  color: var(--color-sale);
  font-size: var(--text-sm);
  font-weight: 700;
}

.stepper {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  margin-top: auto;
  padding-top: var(--space-2);
}

.step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-brand);
  box-shadow: inset 0 -3px 0 var(--color-brand-edge);
  color: var(--color-on-brand);
  cursor: pointer;
}

@media (hover: hover) {
  .step:hover:not(:disabled) {
    background: var(--color-brand-hover);
  }
}

.step:disabled {
  background: var(--color-muted-soft);
  box-shadow: none;
  color: var(--color-ink-soft);
  cursor: not-allowed;
}

.count {
  min-width: 2.5rem;
  font-size: var(--text-lg);
  font-weight: 750;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

@media (min-width: 480px) {
  .drop-product {
    grid-template-columns: 8.5rem minmax(0, 1fr);
  }
}
</style>
