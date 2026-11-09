<script setup>
import { ref } from 'vue'
import { LoaderCircle, ShoppingBag } from 'lucide-vue-next'
import PriceTag from './PriceTag.vue'
import ProductBadges from './ProductBadges.vue'
import ProductImage from './ProductImage.vue'
import WishlistToggle from './WishlistToggle.vue'
import { productFacts, productLink } from '../../data/shop'
import { findGame } from '../../data/games'
import { useCart } from '../../stores/cart'
import { t } from '../../utils/i18n'

/** A product in a list. The cart button adds one and opens the cart drawer. */
const props = defineProps({
  product: { type: Object, required: true },
  eager: { type: Boolean, default: false },
})

const emit = defineEmits(['wished', 'error'])

const accent = props.product.game ? findGame(props.product.game)?.accent : null

const cart = useCart()
const adding = ref(false)

async function addToCart() {
  adding.value = true
  try {
    await cart.add(props.product)
  } catch (exception) {
    emit('error', exception.message)
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <article class="tile">
    <router-link class="tile-link" :to="productLink(product)">
      <ProductImage
        :src="product.imageUrl"
        :is-card="product.imageIsCard"
        :alt="product.name"
        :set-logo="product.setLogoUrl"
        :type="product.type"
        :game="accent"
        :eager="eager"
      />
      <ProductBadges class="badges" :product="product" />
      <span class="name">{{ product.name }}</span>
      <span class="facts">
        <span v-for="fact in productFacts(product)" :key="fact">{{ fact }}</span>
      </span>
    </router-link>

    <!-- Outside the link, because a button can't sit inside one. -->
    <div class="buy-row">
      <PriceTag :product="product" />
      <div class="buttons">
        <WishlistToggle
          :product="product"
          @change="emit('wished', $event)"
          @error="emit('error', $event)"
        />
        <button
          class="cart"
          type="button"
          :disabled="!product.canBuy || adding"
          :aria-label="t('shop.addToCartLabel', { name: product.name })"
          :title="product.canBuy ? t('shop.addToCart') : t('shop.badges.soldOut')"
          @click="addToCart"
        >
          <LoaderCircle v-if="adding" class="spin" :size="20" aria-hidden="true" />
          <ShoppingBag v-else :size="20" aria-hidden="true" />
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.tile {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tile-link {
  display: flex;
  flex: 1;
  flex-direction: column;
  color: var(--color-ink);
  text-decoration: none;
}

.badges {
  margin-top: var(--space-2);
}

.name {
  display: -webkit-box;
  margin-top: var(--space-2);
  overflow: hidden;
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.tile-link:hover .name {
  color: var(--color-brand);
  text-decoration: underline;
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-2);
  margin-top: var(--space-half);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  line-height: 1.35;
}

/* The price and buttons line up along the bottom of a row, however long the names above them are. */
.buy-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.buttons {
  display: flex;
  gap: var(--space-1);
  margin-left: auto;
}

.cart {
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

.cart:hover:not(:disabled) {
  background: var(--color-brand-hover);
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
}

.cart:disabled {
  background: var(--color-muted-soft);
  box-shadow: none;
  color: var(--color-ink-soft);
  cursor: not-allowed;
}
</style>
