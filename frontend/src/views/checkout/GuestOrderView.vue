<script setup>
import { ref, watch } from 'vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import OrderDetails from '../../components/order/OrderDetails.vue'
import OrderStatusBadge from '../../components/order/OrderStatusBadge.vue'
import { orderApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/** An order opened from the link in a mail. Guests need the key in the link, a logged-in owner doesn't. */
const props = defineProps({
  number: { type: String, required: true },
  token: { type: String, default: undefined },
})

const order = ref(null)
const error = ref('')
const loading = ref(true)

async function load() {
  loading.value = true
  error.value = ''
  try {
    order.value = await orderApi.get(props.number, props.token)
  } catch (exception) {
    error.value = exception.status === 404 ? t('orders.placed.notFound') : exception.message
  } finally {
    loading.value = false
  }
}

watch(() => [props.number, props.token], load, { immediate: true })
</script>

<template>
  <div class="container page">
    <p v-if="loading" class="muted" aria-busy="true">{{ t('orders.list.loading') }}</p>
    <CatalogNotice v-else-if="error" :title="error" />
    <template v-else>
      <header class="head">
        <h1>{{ t('orders.detail.title', { number: order.number }) }}</h1>
        <OrderStatusBadge :status="order.status" />
      </header>
      <p class="muted">{{ t('orders.detail.placedOn', { date: formatDate(order.createdAt) }) }}</p>
      <OrderDetails class="details" :order="order" />
      <p class="muted questions">{{ t('orders.detail.questions') }}</p>
    </template>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-6) 0;
}

.muted {
  color: var(--color-ink-soft);
}

.head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.details {
  margin-top: var(--space-5);
}

.questions {
  margin-top: var(--space-6);
  font-size: var(--text-sm);
}
</style>
