<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheck, ShoppingBag, X } from 'lucide-vue-next'
import CartLine from './CartLine.vue'
import CartTotals from './CartTotals.vue'
import FreeShippingBar from './FreeShippingBar.vue'
import FormMessage from '../FormMessage.vue'
import { useCart } from '../../stores/cart'
import { t } from '../../utils/i18n'

/** The cart sliding in from the right, after adding a product or from the cart button in the header. */
const cart = useCart()
const route = useRoute()

const dialog = ref(null)
const closing = ref(false)
const error = ref('')

const items = computed(() => cart.state.cart?.items ?? [])
const added = computed(() => {
  const last = cart.state.lastAdded
  if (!last) return ''
  return last.quantity > 1 ? t('cart.addedMany', { quantity: last.quantity, name: last.name }) : t('cart.added', { name: last.name })
})

function open() {
  error.value = ''
  closing.value = false
  if (!dialog.value.open) dialog.value.showModal()
}

function finishClose() {
  closing.value = false
  dialog.value?.close()
}

function close() {
  if (!dialog.value?.open) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) finishClose()
  else closing.value = true
}

function onAnimationEnd() {
  if (closing.value) finishClose()
}

watch(
  () => cart.state.drawerOpen,
  (isOpen) => (isOpen ? open() : close()),
)

watch(() => route.fullPath, () => cart.closeDrawer())

function onClick(event) {
  if (event.target === dialog.value) cart.closeDrawer()
}
</script>

<template>
  <dialog
    ref="dialog"
    class="drawer"
    :class="{ closing }"
    :aria-label="t('cart.drawerTitle')"
    @click="onClick"
    @cancel.prevent="cart.closeDrawer()"
    @close="cart.closeDrawer()"
    @animationend.self="onAnimationEnd"
  >
    <div class="panel">
      <header class="head">
        <h2 class="title">
          {{ t('cart.drawerTitle') }}
          <span v-if="cart.count.value" class="count">({{ cart.count.value }})</span>
        </h2>
        <button class="close" type="button" :aria-label="t('cart.close')" @click="cart.closeDrawer()">
          <X :size="24" aria-hidden="true" />
        </button>
      </header>

      <div class="body">
        <p v-if="added" class="added" role="status">
          <CircleCheck :size="18" aria-hidden="true" />
          {{ added }}
        </p>
        <FormMessage v-if="error">{{ error }}</FormMessage>

        <div v-if="!items.length" class="empty">
          <ShoppingBag :size="40" aria-hidden="true" />
          <p class="empty-title">{{ t('cart.empty') }}</p>
          <p class="empty-text">{{ t('cart.emptyText') }}</p>
          <button class="btn btn-secondary" type="button" @click="cart.closeDrawer()">{{ t('cart.continueShopping') }}</button>
        </div>

        <template v-else>
          <FreeShippingBar
            class="free"
            :shipping="cart.state.cart.shipping"
            :subtotal-cents="cart.state.cart.subtotalCents"
          />
          <ul class="lines">
            <CartLine
              v-for="item in items"
              :key="item.productId"
              :item="item"
              compact
              @error="error = $event"
              @navigate="cart.closeDrawer()"
            />
          </ul>
        </template>
      </div>

      <footer v-if="items.length" class="foot">
        <CartTotals
          :subtotal-cents="cart.state.cart.subtotalCents"
          :shipping-cents="cart.state.cart.shipping?.priceCents ?? null"
          :total-cents="cart.state.cart.totalCents"
          :vat-cents="cart.state.cart.vatCents"
          :country="cart.state.cart.shipping?.country"
        />
        <p v-if="!cart.state.cart.canCheckout" class="fix">{{ t('cart.fixProblems') }}</p>
        <div class="buttons">
          <router-link
            class="btn btn-primary btn-block"
            :class="{ disabled: !cart.state.cart.canCheckout }"
            :to="cart.state.cart.canCheckout ? '/afrekenen' : '/winkelwagen'"
          >
            {{ t('cart.toCheckout') }}
          </router-link>
          <router-link class="btn btn-secondary btn-block" to="/winkelwagen">{{ t('cart.toCart') }}</router-link>
        </div>
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.drawer {
  position: fixed;
  inset: 0 0 0 auto;
  width: min(28rem, 100vw);
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: var(--color-surface);
  color: var(--color-ink);
}

.drawer[open] {
  animation: slide-in 220ms ease-out;
}

.drawer.closing {
  animation: slide-out 160ms ease-in forwards;
}

.drawer::backdrop {
  background: var(--color-brand-deep);
  opacity: 0.5;
}

@keyframes slide-in {
  from {
    transform: translateX(100%);
  }
}

@keyframes slide-out {
  to {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .drawer[open],
  .drawer.closing {
    animation: none;
  }
}

.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-line);
}

.title {
  font-size: var(--text-xl);
}

.count {
  color: var(--color-ink-soft);
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-weight: 600;
}

.close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  margin-right: calc(var(--space-2) * -1);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
}

.close:hover {
  color: var(--color-brand);
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
}

.added {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  margin-bottom: var(--space-4);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--text-sm);
  font-weight: 600;
}

.added svg {
  flex: none;
  margin-top: var(--space-half);
}

.free {
  margin-bottom: var(--space-2);
}

.lines {
  margin: 0;
  padding: 0;
  list-style: none;
}

.empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding-block: var(--space-7);
  color: var(--color-ink-soft);
  text-align: center;
}

.empty-title {
  color: var(--color-ink);
  font-weight: 700;
}

.empty-text {
  max-width: 24ch;
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
}

.foot {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-top: 1px solid var(--color-line);
  background: var(--color-tint);
}

.fix {
  color: var(--color-warning);
  font-size: var(--text-xs);
  font-weight: 600;
}

.buttons {
  display: grid;
  gap: var(--space-2);
}

.btn.disabled {
  opacity: 0.6;
}
</style>
