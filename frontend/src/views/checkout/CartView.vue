<script setup>
import { computed, ref } from 'vue'
import { ShoppingBag } from 'lucide-vue-next'
import CartLine from '../../components/cart/CartLine.vue'
import CartTotals from '../../components/cart/CartTotals.vue'
import FreeShippingBar from '../../components/cart/FreeShippingBar.vue'
import FormMessage from '../../components/FormMessage.vue'
import CountrySelect from '../../components/account/CountrySelect.vue'
import { useCart } from '../../stores/cart'
import { useRegion } from '../../stores/region'
import { t } from '../../utils/i18n'

const cart = useCart()
const region = useRegion()
const error = ref('')

const current = computed(() => cart.state.cart)
const items = computed(() => current.value?.items ?? [])

const country = computed({
  get: () => region.country.value,
  set: async (value) => {
    error.value = ''
    try {
      await region.setCountry(value)
    } catch (exception) {
      error.value = exception.message
    }
  },
})
</script>

<template>
  <div class="container page">
    <h1>{{ t('cart.title') }}</h1>

    <p v-if="!current && cart.state.loading" class="loading" aria-busy="true">{{ t('cart.loading') }}</p>
    <FormMessage v-else-if="!current && cart.state.error">{{ cart.state.error }}</FormMessage>

    <div v-else-if="!items.length" class="empty">
      <ShoppingBag :size="48" aria-hidden="true" />
      <p class="empty-title">{{ t('cart.empty') }}</p>
      <p>{{ t('cart.emptyText') }}</p>
      <router-link class="btn btn-primary" to="/producten">{{ t('cart.toShop') }}</router-link>
    </div>

    <div v-else class="layout">
      <section class="items" :aria-label="t('cart.title')">
        <FormMessage v-if="error">{{ error }}</FormMessage>
        <ul class="lines">
          <CartLine v-for="item in items" :key="item.productId" :item="item" @error="error = $event" />
        </ul>
        <router-link class="continue" to="/producten">{{ t('cart.continueShopping') }}</router-link>
      </section>

      <aside class="summary">
        <h2 class="summary-title">{{ t('cart.summary') }}</h2>
        <FreeShippingBar :shipping="current.shipping" :subtotal-cents="current.subtotalCents" />
        <CountrySelect v-model="country" :label="t('cart.country')" />
        <p v-if="!current.shipping" class="warning">{{ t('cart.noShipping') }}</p>
        <p v-else class="delivery">{{ t('cart.deliveryTime', { time: current.shipping.deliveryTime }) }}</p>
        <CartTotals
          :subtotal-cents="current.subtotalCents"
          :shipping-cents="current.shipping?.priceCents ?? null"
          :total-cents="current.totalCents"
          :vat-cents="current.vatCents"
          :country="current.shipping?.country"
        />
        <p v-if="!current.canCheckout" class="warning">{{ t('cart.fixProblems') }}</p>
        <router-link v-if="current.canCheckout" class="btn btn-primary btn-block" to="/afrekenen">
          {{ t('cart.toCheckout') }}
        </router-link>
        <button v-else class="btn btn-primary btn-block" type="button" disabled>{{ t('cart.toCheckout') }}</button>
        <div class="methods">
          <p class="methods-title">{{ t('cart.payWith') }}</p>
          <ul class="method-list">
            <li v-for="method in current.paymentMethods" :key="method">{{ t(`cart.paymentMethods.${method}`) }}</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-6) 0;
}

.loading {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
}

.empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  max-width: 28rem;
  margin: var(--space-6) auto 0;
  color: var(--color-ink-soft);
  text-align: center;
}

.empty-title {
  color: var(--color-ink);
  font-size: var(--text-lg);
  font-weight: 700;
}

.empty .btn {
  margin-top: var(--space-3);
}

.layout {
  display: grid;
  gap: var(--space-6);
  margin-top: var(--space-4);
}

.items {
  min-width: 0;
}

.lines {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-line);
}

.continue {
  display: inline-block;
  margin-top: var(--space-4);
  font-size: var(--text-sm);
  font-weight: 600;
}

.summary {
  display: grid;
  align-content: start;
  align-self: start;
  gap: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.summary-title {
  font-size: var(--text-xl);
}

.delivery {
  margin-top: calc(var(--space-2) * -1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.warning {
  color: var(--color-warning);
  font-size: var(--text-sm);
  font-weight: 600;
}

.methods-title {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.method-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin: var(--space-1) 0 0;
  padding: 0;
  list-style: none;
}

.method-list li {
  padding: var(--space-half) var(--space-2);
  border: 1px solid var(--color-line-strong);
  border-radius: var(--radius-xs);
  background: var(--color-surface);
  font-size: var(--text-xs);
  font-weight: 650;
}

@media (min-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr) 22rem;
    gap: var(--space-7);
  }

  .summary {
    position: sticky;
    top: 9rem;
  }
}
</style>
