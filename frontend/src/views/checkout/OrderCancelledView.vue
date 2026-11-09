<script setup>
import { onMounted, ref } from 'vue'
import { CircleX } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { orderApi } from '../../services/api'
import { useCart } from '../../stores/cart'
import { t } from '../../utils/i18n'

/**
 * Where Stripe sends a customer who goes back without paying. The reserved stock goes back right away. Should the
 * payment have come in after all, this page says so instead.
 */
const props = defineProps({
  order: { type: String, default: undefined },
  token: { type: String, default: undefined },
})

const cart = useCart()
const paidAfterAll = ref(false)
const error = ref('')

onMounted(async () => {
  if (!props.order) return
  try {
    const result = await orderApi.cancel(props.order, props.token)
    paidAfterAll.value = result.paidAt != null
  } catch (exception) {
    if (exception.status !== 404) error.value = exception.message
  }
  cart.load()
})
</script>

<template>
  <div class="container page">
    <div v-if="paidAfterAll" class="notice">
      <h1>{{ t('orders.placed.title') }}</h1>
      <p>{{ t('orders.cancelled.paidAfterAll') }}</p>
      <router-link
        class="btn btn-primary"
        :to="{ path: '/bestelling/bedankt', query: { order, token } }"
      >
        {{ t('orders.placed.number', { number: order }) }}
      </router-link>
    </div>
    <div v-else class="notice">
      <CircleX class="icon" :size="40" aria-hidden="true" />
      <h1>{{ t('orders.cancelled.title') }}</h1>
      <p>{{ t('orders.cancelled.text') }}</p>
      <FormMessage v-if="error">{{ error }}</FormMessage>
      <div class="buttons">
        <router-link class="btn btn-primary" to="/afrekenen">{{ t('orders.cancelled.retry') }}</router-link>
        <router-link class="btn btn-secondary" to="/winkelwagen">{{ t('orders.cancelled.toCart') }}</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-7) 0;
}

.notice {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  max-width: 40rem;
}

.icon {
  color: var(--color-ink-soft);
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
</style>
