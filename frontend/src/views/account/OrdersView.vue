<script setup>
import { ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Package } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import OrderStatusBadge from '../../components/order/OrderStatusBadge.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import ProductImage from '../../components/shop/ProductImage.vue'
import { orderApi } from '../../services/api'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  page: { type: String, default: undefined },
})

const result = ref(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await orderApi.mine(Math.max(0, Number(props.page ?? 1) - 1))
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

watch(() => props.page, load, { immediate: true })
</script>

<template>
  <div class="container page">
    <router-link class="back" to="/account">
      <ChevronLeft :size="18" aria-hidden="true" />
      {{ t('orders.list.back') }}
    </router-link>
    <h1>{{ t('orders.list.title') }}</h1>

    <p v-if="loading && !result" class="muted" aria-busy="true">{{ t('orders.list.loading') }}</p>
    <FormMessage v-else-if="error">{{ error }}</FormMessage>
    <div v-else-if="!result.items.length" class="empty">
      <Package :size="40" aria-hidden="true" />
      <p>{{ t('orders.list.empty') }}</p>
      <router-link class="btn btn-primary" to="/producten">{{ t('cart.toShop') }}</router-link>
    </div>
    <template v-else>
      <ul class="orders">
        <li v-for="order in result.items" :key="order.number">
          <router-link
            class="order"
            :to="{ name: 'account-order', params: { number: order.number } }"
            :aria-label="t('orders.list.open', { number: order.number })"
          >
            <ProductImage class="thumb" :src="order.imageUrl" :is-card="order.imageIsCard" />
            <span class="info">
              <span class="number">{{ order.number }}</span>
              <span class="meta">
                {{ formatDate(order.createdAt) }},
                {{ order.itemCount === 1 ? t('orders.list.oneItem') : t('orders.list.items', { count: order.itemCount }) }}
              </span>
            </span>
            <OrderStatusBadge class="status" :status="order.status" />
            <span class="total">{{ formatMoney(order.totalCents, 'EUR') }}</span>
            <ChevronRight class="chevron" :size="20" aria-hidden="true" />
          </router-link>
        </li>
      </ul>
      <PageLinks
        v-if="result.totalPages > 1"
        class="pages"
        :page="result.page + 1"
        :total-pages="result.totalPages"
      />
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 56rem;
  padding-block: var(--space-4) 0;
}

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

.muted {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
}

.empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  margin-top: var(--space-5);
  color: var(--color-ink-soft);
}

.orders {
  margin: var(--space-5) 0 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-line);
}

.order {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr) auto;
  grid-template-areas:
    'thumb info chevron'
    'thumb status total';
  gap: var(--space-1) var(--space-3);
  align-items: center;
  padding-block: var(--space-3);
  border-bottom: 1px solid var(--color-line);
  color: var(--color-ink);
  text-decoration: none;
}

.order:hover .number {
  color: var(--color-brand);
  text-decoration: underline;
}

.thumb {
  grid-area: thumb;
}

.info {
  display: grid;
  grid-area: info;
  min-width: 0;
}

.number {
  font-weight: 700;
}

.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.status {
  grid-area: status;
  justify-self: start;
}

.total {
  grid-area: total;
  font-weight: 700;
  white-space: nowrap;
}

.chevron {
  grid-area: chevron;
  justify-self: end;
  color: var(--color-ink-soft);
}

.pages {
  margin-top: var(--space-5);
}

@media (min-width: 640px) {
  .order {
    grid-template-columns: 3.5rem minmax(0, 1fr) auto auto auto;
    grid-template-areas: 'thumb info status total chevron';
    gap: var(--space-4);
  }
}
</style>
