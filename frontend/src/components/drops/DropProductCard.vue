<script setup>
import { computed } from 'vue'
import { Minus, Plus } from 'lucide-vue-next'
import ProductImage from '../shop/ProductImage.vue'
import { findGame } from '../../data/games'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * A product in a drop: price, limit and what is left. Someone who got through the waiting room gets a counter to
 * put it in the drop cart, up to what is left and what the limits allow.
 */
const props = defineProps({
  product: { type: Object, required: true },
  /** The drop is live and it is this person's turn. */
  shopping: { type: Boolean, default: false },
  inCart: { type: Number, default: 0 },
  /** The most this product can have in the cart within the limits. Null: no limit. */
  room: { type: Number, default: null },
  ordered: { type: Number, default: 0 },
  busy: { type: Boolean, default: false },
  live: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])

const accent = computed(() => (props.product.game ? findGame(props.product.game)?.accent : null))
const max = computed(() => Math.min(props.room ?? 99, props.product.available))
const share = computed(() =>
  props.product.allocated > 0 ? Math.round((props.product.available / props.product.allocated) * 100) : 0,
)

const stockText = computed(() => {
  const product = props.product
  if (product.soldOut) return t('drops.product.soldOut')
  if (product.available === 0) return t('drops.product.allReserved')
  return product.available === 1 ? t('drops.product.oneLeft') : t('drops.product.left', { count: product.available })
})

const low = computed(() => !props.product.soldOut && props.product.available <= Math.max(2, props.product.allocated / 5))
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

      <p class="price">
        <strong>{{ product.priceCents != null ? formatMoney(product.priceCents, 'EUR') : '' }}</strong>
        <s v-if="product.regularPriceCents" class="regular">{{ t('drops.product.regular', { price: formatMoney(product.regularPriceCents, 'EUR') }) }}</s>
      </p>
      <p class="vat">{{ t('shop.inclVat') }}</p>

      <p v-if="product.maxPerCustomer" class="limit">{{ t('drops.product.limit', { count: product.maxPerCustomer }) }}</p>
      <p v-if="ordered > 0" class="limit">{{ t('drops.product.ordered', { count: ordered }) }}</p>

      <div v-if="live || product.soldOut" class="stock" :class="{ low, out: product.available === 0 }">
        <div class="bar" aria-hidden="true"><span :style="{ width: `${share}%` }"></span></div>
        <p class="stock-text" aria-live="polite">{{ stockText }}</p>
        <p v-if="!product.soldOut && product.available === 0" class="hint">{{ t('drops.product.allReservedHint') }}</p>
      </div>
      <p v-else class="allocated">{{ t('drops.product.allocated', { count: product.allocated }) }}</p>

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

.name a:hover {
  color: var(--color-brand);
  text-decoration: underline;
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
.allocated,
.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.limit {
  font-weight: 650;
}

.stock {
  margin-top: var(--space-1);
}

.bar {
  height: 0.375rem;
  overflow: hidden;
  border-radius: var(--radius-round);
  background: var(--color-muted-soft);
}

.bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 0.4s;
}

.low .bar span {
  background: var(--color-warning);
}

.stock-text {
  margin-top: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 700;
}

.low .stock-text {
  color: var(--color-warning);
}

.out .stock-text {
  color: var(--color-sale);
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

.step:hover:not(:disabled) {
  background: var(--color-brand-hover);
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

@media (prefers-reduced-motion: reduce) {
  .bar span {
    transition: none;
  }
}
</style>
