<script setup>
import { computed, ref } from 'vue'
import { LoaderCircle, ShoppingBag } from 'lucide-vue-next'
import PriceTag from './PriceTag.vue'
import ProductBadges from './ProductBadges.vue'
import ProductImage from './ProductImage.vue'
import WishlistToggle from './WishlistToggle.vue'
import { productFacts, productLink } from '../../data/shop'
import { findGame } from '../../data/games'
import { useCart } from '../../stores/cart'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * A product in a list. The cart button adds one and opens the cart drawer. A card from the customer's wishlist
 * (`product.wanted`, on the home page) gets a glowing frame around its picture and a line that says why it has that
 * colour, so the colour is never the only sign.
 */
const props = defineProps({
  product: { type: Object, required: true },
  eager: { type: Boolean, default: false },
})

const emit = defineEmits(['wished', 'error'])

const accent = props.product.game ? findGame(props.product.game)?.accent : null

const wantedNote = computed(() => {
  const wanted = props.product.wanted
  if (!wanted) return ''
  const price = wanted.maxPriceCents != null ? formatMoney(wanted.maxPriceCents, 'EUR') : null
  if (wanted.match === 'ABOVE_MAX') return t('home.wanted.aboveMax', { price })
  if (wanted.match === 'OWNED') return t('home.wanted.owned')
  return price ? t('home.wanted.withinMax', { price }) : t('home.wanted.within')
})

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
      <!-- The frame sits around the picture with a gap, so nothing touches or covers a card image. -->
      <div class="picture" :data-wanted="product.wanted?.match">
        <ProductImage
          :src="product.imageUrl"
          :is-card="product.imageIsCard"
          :alt="product.name"
          :set-logo="product.setLogoUrl"
          :type="product.type"
          :game="accent"
          :eager="eager"
        />
      </div>
      <span v-if="wantedNote" class="wanted-note" :data-wanted="product.wanted.match">
        <span class="dot" aria-hidden="true"></span>
        {{ wantedNote }}
      </span>
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

.picture[data-wanted] {
  --wanted: var(--color-wanted-within);
  padding: var(--frame-gap);
  border: var(--frame-width) solid var(--wanted);
  border-radius: var(--radius-md);
  box-shadow:
    0 0 10px 1px color-mix(in srgb, var(--wanted) 55%, transparent),
    0 0 2px var(--wanted);
}

.picture[data-wanted='OWNED'],
.wanted-note[data-wanted='OWNED'] {
  --wanted: var(--color-wanted-owned);
}

.picture[data-wanted='ABOVE_MAX'],
.wanted-note[data-wanted='ABOVE_MAX'] {
  --wanted: var(--color-wanted-above);
}

.wanted-note {
  --wanted: var(--color-wanted-within);
  display: flex;
  align-items: flex-start;
  gap: var(--space-1);
  margin-top: var(--space-2);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.35;
}

.dot {
  flex: none;
  width: 0.6rem;
  height: 0.6rem;
  /* In the middle of the first line, also when the text runs over two. */
  margin-top: calc((1.35em - 0.6rem) / 2);
  border-radius: 50%;
  background: var(--wanted);
  box-shadow: 0 0 6px color-mix(in srgb, var(--wanted) 70%, transparent);
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

@media (hover: hover) {
  .tile-link:hover .name {
    color: var(--color-brand-ink);
    text-decoration: underline;
  }
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
  transition: transform var(--press-out);
}

.cart:active:not(:disabled) {
  background: var(--color-brand-hover);
  box-shadow: inset 0 -1px 0 var(--color-brand-edge);
  transform: scale(0.92);
  transition: transform var(--press-in);
}

@media (hover: hover) {
  .cart:hover:not(:disabled) {
    background: var(--color-brand-hover);
  }
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

/* Pressed, the product dims under the finger. A moment's wait first, so starting to scroll a list doesn't flash it. */
.tile-link {
  transition: opacity 0.25s;
}

.tile-link:active {
  opacity: 0.7;
  transition: opacity 0s 0.05s;
}
</style>
